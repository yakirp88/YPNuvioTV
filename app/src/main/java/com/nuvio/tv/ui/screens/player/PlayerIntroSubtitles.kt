package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.diagnostics.YpCrashDiagnostics
import com.nuvio.tv.core.intro.*
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.autosync.EmbeddedSubtitleCueStore
import com.nuvio.tv.ui.screens.player.autosync.EmbeddedSubtitleTimelineLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

private suspend fun <T> optional(default: T, block: suspend () -> T): T = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { default }

/** Analysis-only acquisition. Never selects a player track, seeks playback or changes its delay. */
internal suspend fun PlayerRuntimeController.collectIntroSubtitles(imdb: String, season: Int?, episode: Int?,
    duration: Long): List<SubtitleEvidenceAnalyzer.Cue> = supervisorScope {
    val acquisitionDeadline = System.nanoTime() + 60_000_000_000L
    val source = currentStreamUrl.orEmpty()
    val candidates = mutableListOf<IntroCaptionCandidate>()
    val index = async { optional(null) { EmbeddedSubtitleTimelineLoader.load(source, getCurrentHeaders()) } }
    val embedded = async { optional(emptyList()) {
        val plan = index.await()
        if (!plan?.textSources.isNullOrEmpty()) IndexedIntroSubtitles.read(source, getCurrentHeaders(), duration, plan!!.textSources)
        else EmbeddedIntroSubtitles.read(source, getCurrentHeaders(), duration)
    } }
    val movie = contentType.equals("movie", true)
    val id = if (movie) imdb else "$imdb:$season:$episode"
    val public = async {
        try {
            PublicIntroSubtitles.discover(if (movie) "movie" else "series", id).also {
                YpCrashDiagnostics.logDetectionEvent("subtitle_provider", "completed", mapOf("candidate_count" to it.size.toLong()))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val status = when (error) {
                is PublicIntroSubtitles.HttpFailure -> "http_${error.status}"
                is java.io.IOException -> "network_failed"
                else -> "response_invalid"
            }
            YpCrashDiagnostics.logDetectionEvent("subtitle_provider", status)
            emptyList<IntroSubtitleOption>()
        }
    }
    // Reuse the player's installed-provider discovery if it is already in flight.
    repeat(8) { if (uiState.value.isLoadingAddonSubtitles) delay(500) }
    val installed = uiState.value.addonSubtitles.ifEmpty {
        optional(emptyList()) { withTimeoutOrNull(12_000) {
            subtitleRepository.getSubtitles(if (movie) "movie" else "series", imdb, id,
                currentVideoHash, currentVideoSize, currentFilename)
        }.orEmpty() }
    }
    candidates += embedded.await()
    val live = EmbeddedSubtitleCueStore.candidateTracks(source, null)
    candidates += live.filter { it.cues.any { cue -> cue.text.isNotBlank() } }.map { track ->
        IntroCaptionCandidate(track.cues.filter { it.text.isNotBlank() }.map {
            SubtitleEvidenceAnalyzer.Cue(it.startTimeMs, it.endTimeMs, it.text)
        }, track.label.orEmpty(), true)
    }
    YpCrashDiagnostics.logDetectionEvent("embedded_subtitles", metrics = mapOf("embedded_count" to candidates.size.toLong(),
        "cue_count" to candidates.sumOf { it.cues.size }.toLong()))
    val references = index.await()?.tracks.orEmpty().map { it.cues.map { cue -> cue.startTimeMs } }
    val chosen = uiState.value.selectedAddonSubtitle
    val extra = public.await()
    val pool = IntroSubtitleSearch.candidates(extra,
        (uiState.value.addonSubtitles + installed).map { IntroSubtitleOption(it) })
    YpCrashDiagnostics.logDetectionEvent("subtitle_search", metrics = mapOf("candidate_count" to pool.size.toLong(),
        "discovered_count" to (extra.size + installed.size).toLong(),
        "language_count" to pool.map { it.subtitle.lang.lowercase() }.distinct().size.toLong()))
    var rejected = 0
    var checked = 0
    suspend fun read(subtitle: Subtitle): List<SubtitleEvidenceAnalyzer.Cue> = optional(emptyList()) {
        val body = withTimeoutOrNull(9000) {
            if (subtitle.url != chosen?.url && extra.any { it.subtitle.url == subtitle.url } && subtitle.headers.isNullOrEmpty())
                PublicIntroSubtitles.download(subtitle.url)
            else downloadSubtitleBody(subtitle.url, subtitle.lang, subtitle.headers)
        }.orEmpty()
        if (body.length > 2_000_000) emptyList() else withContext(Dispatchers.Default) {
            PlayerSubtitleCueParser.parseFromText(body, subtitle.url)
                .take(12_000).map { SubtitleEvidenceAnalyzer.Cue(it.startTimeMs, it.endTimeMs, it.text) }
        }
    }
    // A selected caption is an explicit, user-timed reference. It is never required for discovery.
    // Use its spoken timestamps too when the media container has no readable subtitle index.
    val selectedCues = chosen?.let { read(it) }.orEmpty()
        .map { SubtitleEvidenceAnalyzer.Cue(it.startMs + uiState.value.subtitleDelayMs.toLong(),
            it.endMs + uiState.value.subtitleDelayMs.toLong(), it.text) }
        .filter { it.startMs >= 0 && it.endMs <= duration }
        .takeIf { IntroSubtitlePolicy.valid(it, duration) }.orEmpty()
    if (selectedCues.isNotEmpty()) candidates += IntroCaptionCandidate(selectedCues, chosen!!.id, false, true)
    val timingReferences = references + candidates.filter { it.embedded || it.selected }.map { caption ->
        caption.cues.filter { IntroSubtitlePolicy.descriptionCount(listOf(it)) == 0 }.map { it.startMs }
    }
    // Three bounded parallel downloads prevent one slow language from consuming the entire pass.
    for (batch in pool.filterNot { it.subtitle.url == chosen?.url }.chunked(3)) {
        if (System.nanoTime() >= acquisitionDeadline) break
        val loaded = batch.map { option -> async { option to read(option.subtitle) } }.map { it.await() }
        for ((option, result) in loaded) {
            checked++
            val descriptions = IntroSubtitlePolicy.descriptionCount(result)
            val offset = if (!IntroSubtitlePolicy.valid(result, duration + 120_000)) null else
                withContext(Dispatchers.Default) { timingReferences.firstNotNullOfOrNull {
                    IntroSubtitlePolicy.alignOffset(result, it, duration)
                } }
            YpCrashDiagnostics.logDetectionEvent("subtitle_candidate", if (offset == null) "subtitle_unavailable" else "subtitle_ready",
                mapOf("cue_count" to result.size.toLong(), "sdh_count" to descriptions.toLong(),
                    "source_code" to if (extra.any { it.subtitle.url == option.subtitle.url }) 3L else 2L))
            if (offset == null) { rejected++; continue }
            val shifted = result.map { SubtitleEvidenceAnalyzer.Cue(it.startMs + offset, it.endMs + offset, it.text) }
                .filter { it.startMs >= 0 && it.endMs <= duration }
            candidates += IntroCaptionCandidate(shifted, option.label, false)
        }
    }
    val usable = candidates.filter { IntroSubtitlePolicy.valid(it.cues, duration) }
    val best = usable.maxByOrNull { candidate ->
        (if (candidate.embedded) 150 else 0) + (if (candidate.selected) 75 else 0) +
            (if (IntroSubtitlePolicy.descriptive(candidate.label)) 100 else 0) +
            IntroSubtitlePolicy.descriptionCount(candidate.cues).coerceAtMost(60) * 4 +
            ((candidate.cues.last().startMs - candidate.cues.first().startMs) * 100 / duration).toInt()
    }
    YpCrashDiagnostics.logDetectionEvent("subtitle_choice", if (best == null) "subtitle_unavailable" else "subtitle_ready",
        mapOf("candidate_count" to usable.size.toLong(), "checked_count" to checked.toLong(), "rejected_count" to rejected.toLong(),
            "cue_count" to (best?.cues?.size ?: 0).toLong(), "source_code" to when {
                best == null -> 0L; best.embedded -> 1L; best.selected -> 2L; else -> 3L },
            "sdh_count" to (best?.let { IntroSubtitlePolicy.descriptionCount(it.cues) } ?: 0).toLong()))
    best?.cues.orEmpty()
}
