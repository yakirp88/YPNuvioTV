package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.diagnostics.YpCrashDiagnostics
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

internal data class IntroEvidenceTrack(
    val subtitle: Subtitle?,
    val videoId: String?,
    val streamFingerprint: String,
    val season: Int?,
    val episode: Int?,
    val offsetMs: Int,
    val started: Boolean
)

/** Playback completion is deliberately absent: it must not cancel in-flight analysis. */
internal fun PlayerUiState.introEvidenceTrack(videoId: String?, fingerprint: String, rendered: Boolean) =
    IntroEvidenceTrack(selectedAddonSubtitle, videoId, fingerprint, currentSeason, currentEpisode,
        subtitleDelayMs, isPlaying || rendered)

/** Collects a selected sidecar subtitle once its VOD duration is known. Never changes playback. */
internal fun PlayerRuntimeController.bindIntroEvidence(store: IntroDetectionStore, profiles: ProfileManager, api: com.nuvio.tv.data.remote.api.IntroDbApi, review: IntroReviewSession) {
    scope.launch {
        runIntroDetectionSafely(onFailure = { error ->
            YpCrashDiagnostics.recordFailure(error)
            YpCrashDiagnostics.markStage("intro_failed")
            review.status("analysis_failed")
            _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.FAILED) }
        }) {
            YpCrashDiagnostics.markStage("player_ready")
            val completed = linkedMapOf<String, String>()
            val checked = mutableMapOf<String, Set<String>>()
            val enabled = combine(store.revision, profiles.activeProfileId) { _, activeId ->
                (activeId == profileId && store.enabled(profileId)) to (if (activeId == profileId) store.cloudKey(profileId) else null)
            }.distinctUntilChanged()
            val track = uiState.map { state ->
                state.introEvidenceTrack(currentVideoId ?: contentId,
                    IntroDetectionStore.fingerprint(currentStreamUrl.orEmpty()), hasRenderedFirstFrame)
            }.distinctUntilChanged()
            val timeline = playbackTimeline.map { it.duration to it.isLive }.distinctUntilChanged()
            combine(enabled, track, timeline) { on, selected, timing -> Triple(on, selected, timing) }
                .collectLatest { (on, selected, timing) ->
                    val subtitle = selected.subtitle
                    val durationMs = timing.first
                    val reviewIdentity = "${selected.videoId}:${selected.streamFingerprint}:$durationMs:${selected.offsetMs}"
                    review.bind(if (on.first) reviewIdentity else "")
                    if (!on.first || !selected.started || selected.videoId == null || timing.second) {
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
                    review.reserve()
                    YpCrashDiagnostics.logDetectionEvent("settings", metrics = mapOf(
                        "cloud_enabled" to if (on.second != null) 1L else 0L,
                        "review_mode" to if (store.snapshot(profileId).submissionMode == com.nuvio.tv.core.intro.IntroSubmissionMode.REVIEW_AT_EPISODE_END) 1L else 0L,
                        "duration_ms" to durationMs, "subtitle_selected" to if (subtitle != null) 1L else 0L))
                    review.status("lookup")
                    _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.SEARCHING_SUBTITLES) }
                    val imdb = selected.videoId.substringBefore(':').takeIf { it.matches(Regex("tt[0-9]{7,10}")) }
                    val movie = contentType.equals("movie", ignoreCase = true)
                    if (imdb == null || (!movie && (selected.season == null || selected.episode == null))) {
                        review.status("missing_metadata")
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.IDLE) }
                        return@collectLatest
                    }
                    YpCrashDiagnostics.markStage("intro_lookup")
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
                            review.status("lookup_failed")
                            _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.FAILED) }
                            return@collectLatest
                        }
                        val data = response.body()
                        val needed = mutableSetOf<String>()
                        if (!movie && data?.intro == null && data?.noIntro != true) needed.add("intro")
                        if (!movie && data?.recap == null && data?.noRecap != true) needed.add("recap")
                        if (data?.outro == null && data?.noOutro != true) needed.add("outro")
                        if (data?.postCredits == null) needed.add("post-credits")
                        YpCrashDiagnostics.logDetectionEvent("lookup_result", metrics = mapOf("missing_count" to needed.size.toLong(), "http_status" to response.code().toLong()))
                        checked[dbKey] = needed
                        needed
                    }
                    if (missing.isEmpty()) {
                        review.status("already_reported")
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
                        // Preserve the existing proposal/outcome instead of resetting it.
                        review.status(completed.getValue(attemptKey))
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.COMPLETE) }
                        return@collectLatest
                    }
                    // Wait for track/episode transitions to settle. collectLatest cancels obsolete work.
                    try {
                        _uiState.update { it.copy(introDetectionPhase = if (subtitle == null) IntroDetectionPhase.ANALYZING_MEDIA else IntroDetectionPhase.SEARCHING_SUBTITLES) }
                        review.status("subtitles")
                        YpCrashDiagnostics.markStage("intro_subtitles")
                        delay(750)
                        val body = if (subtitle == null) "" else try { downloadSubtitleBody(subtitle.url, subtitle.lang, subtitle.headers) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { "" }
                        YpCrashDiagnostics.logDetectionEvent("subtitle_download", metrics = mapOf("body_chars" to body.length.toLong()))
                        // Do not retain transcript, source URL, headers, or credentials in the result cache.
                        val hints = withContext(Dispatchers.Default) {
                            require(body.length <= 2_000_000) { "Subtitle exceeds evidence collection limit" }
                            val cues = PlayerSubtitleCueParser.parseFromText(body, subtitle?.url.orEmpty())
                            YpCrashDiagnostics.logDetectionEvent("subtitle_parsed", metrics = mapOf("cue_count" to cues.size.toLong()))
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
                                YpCrashDiagnostics.logDetectionEvent("cloud_request", metrics = mapOf("cue_count" to cues.size.toLong()))
                                review.status("cloud")
                                YpCrashDiagnostics.markStage("intro_cloud")
                            proposals = com.nuvio.tv.core.intro.GeminiIntroAnalyzer.analyze(cloudKey, cues, durationMs, missing)
                                cloudStatus = "completed"
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { cloudStatus = cloudFailureStatus(error) }
                        }
                        YpCrashDiagnostics.logDetectionEvent("cloud_result", cloudStatus, mapOf("proposal_count" to proposals.size.toLong()))
                        review.offer(reviewIdentity, proposals, durationMs)
                        // Subtitle results are proposals, not certainty: continue quietly with visual evidence.
                        if (cloudKey != null && store.cloudKey(profileId) == cloudKey) {
                            _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.ANALYZING_MEDIA) }
                            try {
                                review.status("media")
                                YpCrashDiagnostics.markStage("intro_media")
                            val frames = com.nuvio.tv.core.intro.VideoEvidenceSampler.sample(
                                    currentStreamUrl, getCurrentHeaders(), durationMs,
                                    missing.any { it == "intro" || it == "recap" },
                                    missing.any { it == "outro" || it == "post-credits" })
                                YpCrashDiagnostics.logDetectionEvent("media_sample", metrics = mapOf("frame_count" to frames.size.toLong()))
                                if (frames.isNotEmpty()) {
                                    currentCoroutineContext().ensureActive()
                                    val visual = com.nuvio.tv.core.intro.GeminiIntroAnalyzer.analyze(
                                        cloudKey, evidenceCues, durationMs, missing, frames)
                                    if (visual.isNotEmpty()) proposals = visual
                                    cloudStatus = "visual_completed"
                                } else if (cloudStatus == "not_requested") cloudStatus = "visual_unavailable"
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { if (proposals.isEmpty()) cloudStatus = cloudFailureStatus(error) }
                        }
                        currentCoroutineContext().ensureActive()
                        if (profiles.activeProfileId.value != profileId) return@collectLatest
                        store.saveEvidence(profileId, identity, durationMs, selected.offsetMs.toLong(), hints, proposals, cloudStatus)
                        review.offer(reviewIdentity, proposals, durationMs)
                        review.status(when {
                            proposals.isNotEmpty() && store.snapshot(profileId).submissionMode == com.nuvio.tv.core.intro.IntroSubmissionMode.REVIEW_AT_EPISODE_END -> "proposals"
                            cloudStatus.startsWith("http_") || cloudStatus == "cloud_failed" -> cloudStatus
                            cloudKey == null -> "cloud_disabled"
                            store.snapshot(profileId).submissionMode != com.nuvio.tv.core.intro.IntroSubmissionMode.REVIEW_AT_EPISODE_END -> "automatic_unavailable"
                            body.isBlank() && cloudStatus == "not_requested" -> "no_evidence"
                            else -> "no_proposals"
                        })
                        completed[attemptKey] = review.state.value.status
                        if (completed.size > 20) completed.remove(completed.keys.first())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        YpCrashDiagnostics.recordFailure(error)
                        review.status("analysis_failed")
                        // Optional collection must never interrupt playback or log authenticated URLs.
                    } finally {
                        // Job completion is not proof that its proposed boundaries are accurate.
                        _uiState.update { it.copy(introDetectionPhase = IntroDetectionPhase.COMPLETE) }
                    }
                }
        }
    }
}
