package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.intro.SubtitleEvidenceAnalyzer
import com.nuvio.tv.core.intro.IntroDetectionPhase
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.IntroDetectionStore
import com.nuvio.tv.domain.model.Subtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class IntroEvidenceTrack(
    val subtitle: Subtitle?,
    val videoId: String?,
    val streamFingerprint: String,
    val season: Int?,
    val episode: Int?,
    val offsetMs: Int,
    val started: Boolean,
    val endedOrFailed: Boolean
)

/** Collects a selected sidecar subtitle once its VOD duration is known. Never changes playback. */
internal fun PlayerRuntimeController.bindIntroEvidence(store: IntroDetectionStore, profiles: ProfileManager, api: com.nuvio.tv.data.remote.api.IntroDbApi, review: IntroReviewSession) {
    scope.launch {
        runIntroDetectionSafely(onFailure = {
            review.bind("")
            _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.FAILED) }
        }) {
            val completed = linkedSetOf<String>()
            val checked = mutableMapOf<String, Set<String>>()
            val enabled = combine(store.revision, profiles.activeProfileId) { _, activeId ->
                (activeId == profileId && store.enabled(profileId)) to (if (activeId == profileId) store.cloudKey(profileId) else null)
            }.distinctUntilChanged()
            val track = uiState.map { state ->
                IntroEvidenceTrack(
                    state.selectedAddonSubtitle,
                    currentVideoId ?: contentId,
                    IntroDetectionStore.fingerprint(currentStreamUrl.orEmpty()),
                    state.currentSeason,
                    state.currentEpisode,
                    state.subtitleDelayMs,
                    state.isPlaying || hasRenderedFirstFrame,
                    state.playbackEnded || state.error != null
                )
            }.distinctUntilChanged()
            val timeline = playbackTimeline.map { it.duration to it.isLive }.distinctUntilChanged()
            combine(enabled, track, timeline) { on, selected, timing -> Triple(on, selected, timing) }
                .collectLatest { (on, selected, timing) ->
                    val subtitle = selected.subtitle
                    val durationMs = timing.first
                    val reviewIdentity = "${selected.videoId}:${selected.streamFingerprint}:$durationMs:${selected.offsetMs}"
                    review.bind(if (on.first) reviewIdentity else "")
                    if (!on.first || !selected.started || selected.endedOrFailed || selected.videoId == null || timing.second) {
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.IDLE) }
                        return@collectLatest
                    }
                    if (durationMs <= 0) {
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.SEARCHING_SUBTITLES) }
                        // Allow add-on subtitle discovery to finish; no permanent loading badge.
                        delay(8000)
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.AWAITING_MEDIA_ANALYZER) }
                        return@collectLatest
                    }
                    _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.SEARCHING_SUBTITLES) }
                    val imdb = selected.videoId.substringBefore(':').takeIf { it.matches(Regex("tt[0-9]{7,10}")) }
                    val movie = contentType.equals("movie", ignoreCase = true)
                    if (imdb == null || (!movie && (selected.season == null || selected.episode == null))) {
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.IDLE) }
                        return@collectLatest
                    }
                    val dbKey = "$imdb:${selected.season}:${selected.episode}:$movie"
                    val missing = checked[dbKey] ?: run {
                        val response = try {
                            kotlinx.coroutines.withTimeoutOrNull(12_000) {
                                api.getSegments(imdb, if (movie) null else selected.season,
                                    if (movie) null else selected.episode, true.takeIf { movie })
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                        // Failure/timeout/429 is not proof of missing data: defer all analysis.
                        if (response == null || (!response.isSuccessful && response.code() != 404) ||
                            (response.isSuccessful && response.body() == null)) {
                            _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.FAILED) }
                            return@collectLatest
                        }
                        val data = response.body()
                        val needed = mutableSetOf<String>()
                        if (!movie && data?.intro == null && data?.noIntro != true) needed.add("intro")
                        if (!movie && data?.recap == null && data?.noRecap != true) needed.add("recap")
                        if (data?.outro == null && data?.noOutro != true) needed.add("outro")
                        if (data?.postCredits == null) needed.add("post-credits")
                        checked[dbKey] = needed
                        needed
                    }
                    if (missing.isEmpty()) {
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.COMPLETE) }
                        return@collectLatest
                    }
                    if (subtitle == null) {
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.SEARCHING_SUBTITLES) }
                        delay(8000)
                    }
                    val identity = listOf(
                        selected.videoId, selected.season, selected.episode, selected.streamFingerprint,
                        durationMs, selected.offsetMs, IntroDetectionStore.fingerprint(subtitle?.url.orEmpty())
                    ).joinToString(":")
                    val attemptKey = identity + if (on.second != null) ":cloud" else ":local"
                    if (attemptKey in completed) {
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.COMPLETE) }
                        return@collectLatest
                    }
                    // Wait for track/episode transitions to settle. collectLatest cancels obsolete work.
                    try {
                        _uiState.update { it.copy(introDetectionPhase = if (subtitle == null) IntroDetectionPhase.ANALYZING_MEDIA else IntroDetectionPhase.SEARCHING_SUBTITLES) }
                        delay(750)
                        val body = if (subtitle == null) "" else try { downloadSubtitleBody(subtitle.url, subtitle.lang, subtitle.headers) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { "" }
                        // Do not retain transcript, source URL, headers, or credentials in the result cache.
                        val hints = withContext(Dispatchers.Default) {
                            require(body.length <= 2_000_000) { "Subtitle exceeds evidence collection limit" }
                            val cues = PlayerSubtitleCueParser.parseFromText(body, subtitle?.url.orEmpty())
                            SubtitleEvidenceAnalyzer.analyze(
                                cues.map { SubtitleEvidenceAnalyzer.Cue(it.startTimeMs, it.endTimeMs, it.text) },
                                durationMs,
                                selected.offsetMs.toLong()
                            )
                        }
                        currentCoroutineContext().ensureActive()
                        if (profiles.activeProfileId.value != profileId) return@collectLatest
                        var cloudStatus = "not_requested"
                        var proposals = emptyList<com.nuvio.tv.core.intro.AiSegmentProposal>()
                        var evidenceCues = emptyList<SubtitleEvidenceAnalyzer.Cue>()
                        val cloudKey = on.second
                        if (cloudKey != null && store.cloudKey(profileId) == cloudKey) {
                            val cues = withContext(Dispatchers.Default) {
                                PlayerSubtitleCueParser.parseFromText(body, subtitle?.url.orEmpty()).map {
                                    com.nuvio.tv.core.intro.SubtitleEvidenceAnalyzer.Cue(
                                        Math.addExact(it.startTimeMs, selected.offsetMs.toLong()),
                                        Math.addExact(it.endTimeMs, selected.offsetMs.toLong()), it.text
                                    )
                                }
                            }
                            evidenceCues = cues
                            try {
                                proposals = com.nuvio.tv.core.intro.GeminiIntroAnalyzer.analyze(cloudKey, cues, durationMs, missing)
                                cloudStatus = "completed"
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { cloudStatus = "failed" }
                        }
                        // Subtitle results are proposals, not certainty: continue quietly with visual evidence.
                        if (cloudKey != null && store.cloudKey(profileId) == cloudKey) {
                            _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.ANALYZING_MEDIA) }
                            try {
                                val frames = com.nuvio.tv.core.intro.VideoEvidenceSampler.sample(
                                    currentStreamUrl, getCurrentHeaders(), durationMs,
                                    missing.any { it == "intro" || it == "recap" },
                                    missing.any { it == "outro" || it == "post-credits" })
                                if (frames.isNotEmpty()) {
                                    currentCoroutineContext().ensureActive()
                                    val visual = com.nuvio.tv.core.intro.GeminiIntroAnalyzer.analyze(
                                        cloudKey, evidenceCues, durationMs, missing, frames)
                                    if (visual.isNotEmpty()) proposals = visual
                                    cloudStatus = "visual_completed"
                                } else cloudStatus = "visual_unavailable"
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { cloudStatus = "visual_failed" }
                        }
                        currentCoroutineContext().ensureActive()
                        if (profiles.activeProfileId.value != profileId) return@collectLatest
                        store.saveEvidence(profileId, identity, durationMs, selected.offsetMs.toLong(), hints, proposals, cloudStatus)
                        review.offer(reviewIdentity, proposals, durationMs)
                        completed.add(attemptKey)
                        if (completed.size > 20) completed.remove(completed.first())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Optional collection must never interrupt playback or log authenticated URLs.
                    } finally {
                        // Job completion is not proof that its proposed boundaries are accurate.
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.COMPLETE) }
                    }
                }
        }
    }
}
