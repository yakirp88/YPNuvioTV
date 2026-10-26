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
    val embedded = async { optional(emptyList()) { EmbeddedIntroSubtitles.read(source, getCurrentHeaders(), duration) } }
    val index = async { optional(null) { EmbeddedSubtitleTimelineLoader.load(source, getCurrentHeaders()) } }
    val movie = contentType.equals("movie", true)
    val id = if (movie) imdb else "$imdb:$season:$episode"
    val public = async { optional(emptyList()) { PublicIntroSubtitles.discover(if (movie) "movie" else "series", id) } }
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
    val pool = (listOfNotNull(chosen) + installed + extra).distinctBy { it.url }
        .sortedByDescending { (if (it.url == chosen?.url) 1000 else 0) + (if (IntroSubtitlePolicy.descriptive(it.id)) 100 else if (it.lang.startsWith("en")) 20 else 0) }
        .take(8)
    YpCrashDiagnostics.logDetectionEvent("subtitle_search", metrics = mapOf("candidate_count" to pool.size.toLong()))
    var rejected = 0
    for (subtitle in pool) {
        if (System.nanoTime() >= acquisitionDeadline) break
        val result = optional<List<SubtitleEvidenceAnalyzer.Cue>>(emptyList()) {
            val body = withTimeoutOrNull(9000) {
                if (extra.any { it.url == subtitle.url } && subtitle.headers.isNullOrEmpty()) PublicIntroSubtitles.download(subtitle.url)
                else downloadSubtitleBody(subtitle.url, subtitle.lang, subtitle.headers)
            }.orEmpty()
            if (body.length > 2_000_000) emptyList() else withContext(Dispatchers.Default) {
                PlayerSubtitleCueParser.parseFromText(body, subtitle.url)
                    .take(12_000).map { SubtitleEvidenceAnalyzer.Cue(it.startTimeMs, it.endTimeMs, it.text) }
            }
        }
        if (!IntroSubtitlePolicy.valid(result, duration + 120_000)) { rejected++; continue }
        val selected = subtitle.url == chosen?.url
        val offset = if (selected) uiState.value.subtitleDelayMs.toLong() else withContext(Dispatchers.Default) { references.firstNotNullOfOrNull {
            IntroSubtitlePolicy.alignOffset(result, it, duration)
        } }
        if (offset == null) { rejected++; continue }
        val shifted = result.map { SubtitleEvidenceAnalyzer.Cue(it.startMs + offset, it.endMs + offset, it.text) }
            .filter { it.startMs >= 0 && it.endMs <= duration }
        candidates += IntroCaptionCandidate(shifted, subtitle.id, false, selected)
    }
    val usable = candidates.filter { IntroSubtitlePolicy.valid(it.cues, duration) }
    val best = usable.maxByOrNull { candidate ->
        (if (candidate.embedded) 150 else 0) + (if (candidate.selected) 75 else 0) +
            (if (IntroSubtitlePolicy.descriptive(candidate.label)) 100 else 0) +
            IntroSubtitlePolicy.descriptionCount(candidate.cues).coerceAtMost(60) * 4 +
            ((candidate.cues.last().startMs - candidate.cues.first().startMs) * 100 / duration).toInt()
    }
    YpCrashDiagnostics.logDetectionEvent("subtitle_choice", if (best == null) "subtitle_unavailable" else "subtitle_ready",
        mapOf("candidate_count" to usable.size.toLong(), "rejected_count" to rejected.toLong(),
            "cue_count" to (best?.cues?.size ?: 0).toLong(), "source_code" to when {
                best == null -> 0L; best.embedded -> 1L; best.selected -> 2L; else -> 3L },
            "sdh_count" to (best?.let { IntroSubtitlePolicy.descriptionCount(it.cues) } ?: 0).toLong()))
    best?.cues.orEmpty()
}
