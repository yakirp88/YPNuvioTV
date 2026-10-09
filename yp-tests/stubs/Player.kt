package com.nuvio.tv.ui.screens.player
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import android.graphics.Bitmap

data class FakeUiState(val introDbReportingActive: Boolean = false, val activeSkipInterval: String? = null,
    val showPauseOverlay: Boolean = false, val postPlayMode: String? = null, val showControls: Boolean = false)
data class FakeTimeline(val isLive: Boolean = false)
class PlayerRuntimeController {
    var contentId: String? = "tt0903747"
    var currentVideoId: String? = "tt0903747:1:1"
    var currentSeason: Int? = 1
    var currentEpisode: Int? = 1
    var contentType: String? = "series"
    val playbackTimeline = MutableStateFlow(FakeTimeline())
    val _uiState = MutableStateFlow(FakeUiState())
    var nextEpisodeAutoPlayJob: Job? = null
    var pauseOverlayJob: Job? = null
    var position = 72_123L
    var duration = 3600_000L
    var playing = true
    var controlsHidden = false
    fun currentPlaybackPositionMs(): Long? = position
    fun currentPlaybackDurationMs(): Long = duration
    fun hasActivePlayIntent() = playing
    fun hideControls() { controlsHidden = true }
    fun scheduleHideControls() { controlsHidden = false }
    fun setPlaybackPaused(paused: Boolean) { playing = !paused }
    fun seekPlaybackTo(at: Long) { position = at }
}
suspend fun PlayerRuntimeController.captureReportFrame(atMs: Long? = null): Bitmap? {
    if (atMs != null) position = atMs
    return Bitmap(position)
}
