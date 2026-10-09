package com.nuvio.tv.ui.screens.player

import android.graphics.Bitmap
import com.nuvio.tv.data.introdb.IntroDbReportRepository
import com.nuvio.tv.data.introdb.IntroDbReportRules
import com.nuvio.tv.data.introdb.ReportMedia
import com.nuvio.tv.data.introdb.ReportSegment
import com.nuvio.tv.domain.repository.MetaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ReportStage { IDLE, CHOOSE, RECORDING, REVIEW }
data class IntroDbReportState(
    val media: ReportMedia? = null,
    val checking: Boolean = true,
    val available: Set<ReportSegment> = emptySet(),
    val stage: ReportStage = ReportStage.IDLE,
    val segment: ReportSegment? = null,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val durationMs: Long = 0,
    val startImage: Bitmap? = null,
    val endImage: Bitmap? = null,
    val previewing: Boolean = false,
    val sending: Boolean = false,
    val recordingPlayerControls: Boolean = false,
    val calibratingStart: Boolean? = null,
    val message: String? = null
) {
    val active get() = stage != ReportStage.IDLE
    val allowsPlayerControls get() = !active || (stage == ReportStage.RECORDING && recordingPlayerControls)
    val canStart get() = !checking && media != null && available.isNotEmpty() && !active
}

internal class IntroDbReportCoordinator(
    private val controller: PlayerRuntimeController,
    private val repository: IntroDbReportRepository,
    private val metadata: MetaRepository,
    private val scope: CoroutineScope
) {
    private val mutable = MutableStateFlow(IntroDbReportState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var preview: Job? = null
    private var mediaIdentity = ""

    fun hasApiKey() = repository.apiKey() != null
    fun saveApiKey(value: String) = repository.saveApiKey(value)

    fun load(force: Boolean = false) {
        val c = controller
        val identity = "${c.contentId}:${c.currentVideoId}:${c.currentSeason}:${c.currentEpisode}"
        if (!force && identity == mediaIdentity) return
        mediaIdentity = identity
        request?.cancel(); preview?.cancel()
        setActive(false)
        mutable.value = IntroDbReportState()
        request = scope.launch {
            try {
                val type = c.contentType.orEmpty()
                if (!repository.enabled || type !in setOf("movie", "series", "tv") || c.playbackTimeline.value.isLive) {
                    mutable.update { it.copy(checking = false) }; return@launch
                }
                val id = c.contentId.orEmpty()
                val video = c.currentVideoId.orEmpty()
                val imdb = listOf(video.substringBefore(':'), id.substringBefore(':'),
                    metadata.getCachedMeta(type, id)?.imdbId.orEmpty())
                    .firstOrNull { Regex("tt[0-9]{7,8}").matches(it) }
                val media = imdb?.let { ReportMedia(it, if (type == "movie") null else c.currentSeason,
                    if (type == "movie") null else c.currentEpisode, type == "movie") }
                if (media?.valid != true) { mutable.update { it.copy(checking = false) }; return@launch }
                val existing = withTimeoutOrNull(20_000L) { repository.existing(media) } ?: error("CHECK_TIMEOUT")
                mutable.value = IntroDbReportState(media = media, checking = false,
                    available = IntroDbReportRules.available(media, existing + repository.submitted(media)))
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.update { it.copy(checking = false, message = "check_failed") } }
        }
    }

    /** Called synchronously by the button, BEFORE choosing a segment or doing IO. */
    fun begin(capturedPositionMs: Long? = null) {
        val position = capturedPositionMs ?: controller.currentPlaybackPositionMs() ?: return
        val s = mutable.value
        if (!s.canStart || !hasApiKey()) return
        val duration = controller.currentPlaybackDurationMs()
        if (duration <= 0 || controller.playbackTimeline.value.isLive) return
        setActive(true)
        mutable.update { it.copy(stage = ReportStage.CHOOSE, startMs = position.coerceIn(0, duration),
            durationMs = duration, message = null) }
        controller.hideControlsJob?.cancel()
        controller._uiState.update { it.copy(showControls = true) }
        // Thumbnails are reconstructed at the saved boundaries after Finish.
    }

    fun hasDraft(): Boolean {
        val s = mutable.value
        return s.media?.let { repository.draft(it)?.segment in s.available } == true
    }

    fun resumeDraft() {
        val s = mutable.value
        if (s.stage != ReportStage.CHOOSE) return
        val draft = s.media?.let { repository.draft(it) } ?: return
        if (draft.segment !in s.available || !IntroDbReportRules.validRange(draft.start, draft.end, s.durationMs)) return
        controller.setPlaybackPaused(true)
        mutable.update { it.copy(stage = ReportStage.REVIEW, segment = draft.segment, startMs = draft.start,
            endMs = draft.end, previewing = true, message = null) }
        preview = scope.launch {
            val start = controller.captureReportFrame(draft.start)
            mutable.update { it.copy(startImage = start) }
            val end = controller.captureReportFrame(draft.end)
            mutable.update { it.copy(endImage = end, previewing = false) }
        }
    }

    fun choose(segment: ReportSegment) {
        if (mutable.value.stage != ReportStage.CHOOSE || segment !in mutable.value.available) return
        mutable.update { it.copy(stage = ReportStage.RECORDING, segment = segment, recordingPlayerControls = true) }
        controller.setPlaybackPaused(false)
        controller.hideControls()
        controller.scheduleHideControls()
    }

    fun showPlayerControls() {
        if (mutable.value.stage != ReportStage.RECORDING) return
        mutable.update { it.copy(recordingPlayerControls = true) }
        controller._uiState.update { it.copy(showControls = true) }
    }

    fun focusReportControls() {
        if (mutable.value.stage != ReportStage.RECORDING) return
        controller.hideControlsJob?.cancel()
        mutable.update { it.copy(recordingPlayerControls = false) }
    }

    fun finish() {
        val s = mutable.value
        if (s.stage != ReportStage.RECORDING) return
        val end = controller.currentPlaybackPositionMs() ?: return
        if (!IntroDbReportRules.validRange(s.startMs, end, s.durationMs)) {
            mutable.update { it.copy(message = "invalid_range") }; return
        }
        s.media?.let { repository.saveDraft(it, requireNotNull(s.segment), s.startMs, end, s.durationMs) }
        controller.setPlaybackPaused(true)
        controller.hideControls()
        mutable.update { it.copy(stage = ReportStage.REVIEW, endMs = end, previewing = true,
            recordingPlayerControls = false, message = null) }
        preview?.cancel()
        preview = scope.launch {
            val start = controller.captureReportFrame(s.startMs)
            mutable.update { it.copy(startImage = start) }
            val finish = controller.captureReportFrame(end)
            mutable.update { it.copy(endImage = finish, previewing = false) }
        }
    }

    /** Open a boundary on the paused main surface, without changing its saved time. */
    fun selectBoundary(start: Boolean) {
        val s = mutable.value
        if (s.stage != ReportStage.REVIEW || s.sending || s.previewing) return
        mutable.update { it.copy(calibratingStart = start, previewing = true, message = null) }
        preview?.cancel()
        preview = scope.launch { renderBoundary(start) }
    }

    fun endCalibration() {
        if (mutable.value.stage == ReportStage.REVIEW) mutable.update { it.copy(calibratingStart = null) }
    }

    /** Coalesce remote repeats; only the latest requested boundary may publish a frame. */
    fun adjust(start: Boolean, delta: Long) {
        val s = mutable.value
        if (s.stage != ReportStage.REVIEW || s.sending || (s.previewing && s.calibratingStart == null)) return
        val (newStart, newEnd) = IntroDbReportRules.adjust(s.startMs, s.endMs, s.durationMs, start, delta)
        if (newStart == s.startMs && newEnd == s.endMs) return
        mutable.update { it.copy(startMs = newStart, endMs = newEnd, previewing = true, message = null) }
        s.media?.let { repository.saveDraft(it, requireNotNull(s.segment), newStart, newEnd, s.durationMs) }
        // Keep one decoder operation in flight. Repeated presses replace the target,
        // rather than cancelling every seek and starving the video surface of frames.
        if (preview?.isActive != true) preview = scope.launch { renderBoundary(start) }
    }

    private suspend fun renderBoundary(start: Boolean) {
        while (true) {
            val target = mutable.value
            if (target.stage != ReportStage.REVIEW) return
            val timestamp = if (start) target.startMs else target.endMs
            val frame = controller.captureReportFrame(timestamp)
            val current = mutable.value
            if (current.stage != ReportStage.REVIEW) return
            if ((if (start) current.startMs else current.endMs) != timestamp) continue
            mutable.update { it.copy(previewing = false,
                startImage = if (start) frame else it.startImage, endImage = if (start) it.endImage else frame) }
            return
        }
    }

    fun send() {
        val s = mutable.value
        val media = s.media ?: return
        val segment = s.segment ?: return
        if (s.stage != ReportStage.REVIEW || s.sending || s.previewing) return
        mutable.update { it.copy(sending = true, message = null) }
        request = scope.launch {
            try {
                val sent = repository.submit(media, segment, s.startMs, s.endMs, s.durationMs)
                repository.markSubmitted(media, segment)
                repository.removeDraft(media)
                val remaining = mutable.value.available - segment
                close(restore = true)
                mutable.update { it.copy(available = remaining, message = if (sent) "sent" else "exists") }
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                mutable.update { it.copy(sending = false, message = when (failure.message) {
                    "NO_KEY", "HTTP_401", "HTTP_403" -> "key_failed"
                    "HTTP_429" -> "rate_limit"
                    "HTTP_400", "HTTP_422" -> "rejected"
                    else -> "send_failed"
                }) }
            }
        }
    }

    fun keepDraft() { if (!mutable.value.sending) close(restore = true) }

    fun cancel() {
        if (mutable.value.sending) return
        // Explicit cancel discards the draft. Disposal/offline failure preserves it.
        mutable.value.media?.let { repository.removeDraft(it) }
        close(restore = true)
    }
    fun dispose() { request?.cancel(); preview?.cancel(); close(restore = false) }
    fun clearMessage() { mutable.update { it.copy(message = null) } }

    private fun close(restore: Boolean) {
        val s = mutable.value
        preview?.cancel()
        if (restore && s.stage == ReportStage.REVIEW) {
            controller.seekPlaybackTo(s.endMs)
            controller.setPlaybackPaused(false)
        }
        setActive(false)
        mutable.update { it.copy(stage = ReportStage.IDLE, segment = null, startImage = null, endImage = null,
            previewing = false, sending = false, recordingPlayerControls = false, calibratingStart = null, message = null) }
        if (restore) controller.scheduleHideControls()
    }

    private fun setActive(active: Boolean) {
        if (!active) {
            controller._uiState.update { it.copy(introDbReportingActive = false) }
            return
        }
        controller._uiState.update { it.copy(introDbReportingActive = true, activeSkipInterval = null,
            showPauseOverlay = false, postPlayMode = null) }
        if (active) {
            controller.nextEpisodeAutoPlayJob?.cancel()
            controller.nextEpisodeAutoPlayJob = null
            controller.pauseOverlayJob?.cancel()
        }
    }
}
