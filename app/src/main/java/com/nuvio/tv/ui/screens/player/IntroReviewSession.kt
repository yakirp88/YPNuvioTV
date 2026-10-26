package com.nuvio.tv.ui.screens.player

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.nuvio.tv.core.intro.AiSegmentProposal
import com.nuvio.tv.core.intro.IntroDbPublisher
import com.nuvio.tv.core.intro.IntroSubmissionMode
import com.nuvio.tv.core.intro.VideoEvidenceSampler
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.IntroDetectionStore
import com.nuvio.tv.data.remote.api.IntroDbApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class IntroReviewState(
    val segments: List<IntroReviewSegment> = emptyList(),
    val submitting: Boolean = false,
    val status: String = "waiting",
    val failed: Boolean = false
)

/** Human approval is the verification step here; coarse model proposals never auto-submit. */
internal class IntroReviewSession(
    private val player: PlayerRuntimeController,
    private val store: IntroDetectionStore,
    private val api: IntroDbApi,
    private val profiles: ProfileManager,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(IntroReviewState())
    val state = _state.asStateFlow()
    private var identity = ""
    private var dismissed = false
    private var url = ""
    private var headers = emptyMap<String, String>()
    private val jobs = mutableMapOf<String, Job>()
    private var submissionJob: Job? = null
    private val attempted = mutableSetOf<String>()

    fun bind(mediaIdentity: String) {
        if (identity == mediaIdentity) return
        jobs.values.forEach { it.cancel() }; jobs.clear()
        submissionJob?.cancel()
        identity = mediaIdentity
        dismissed = false
        attempted.clear()
        _state.value = IntroReviewState()
        player._uiState.update { it.copy(introReviewPending = false) }
    }

    fun reserve() {
        if (dismissed || identity.isEmpty()) return
        player._uiState.update { it.copy(introReviewPending = true, postPlayMode = null) }
    }

    fun status(value: String) {
        if (dismissed) return
        _state.update { it.copy(status = com.nuvio.tv.core.diagnostics.YpCrashReportText.safeDetectionStatus(value)) }
        com.nuvio.tv.core.diagnostics.YpCrashDiagnostics.recordDetectionStatus(value)
    }

    fun dismiss() {
        if (_state.value.submitting) return
        dismissed = true
        jobs.values.forEach { it.cancel() }; jobs.clear()
        _state.value = IntroReviewState()
        player._uiState.update { it.copy(introReviewPending = false) }
    }

    suspend fun offer(mediaIdentity: String, proposals: List<AiSegmentProposal>, durationMs: Long) {
        if (identity != mediaIdentity || dismissed || proposals.isEmpty() ||
            profiles.activeProfileId.value != player.profileId) return
        if (store.snapshot(player.profileId).submissionMode != IntroSubmissionMode.REVIEW_AT_EPISODE_END) return
        if (identity != mediaIdentity || dismissed || profiles.activeProfileId.value != player.profileId) return
        // Once visible, a late visual pass must not replace the user's edits or selected images.
        if (player.uiState.value.playbackEnded && _state.value.segments.isNotEmpty()) return
        url = player.getCurrentStreamUrl()
        headers = player.getCurrentHeaders().toMap()
        val segments = proposals.map { proposal ->
            IntroReviewSegment(
                id = "${proposal.type}:${proposal.startMs}:${proposal.endMs}",
                type = when (proposal.type) {
                    "intro" -> IntroReviewSegmentType.INTRO
                    "recap" -> IntroReviewSegmentType.RECAP
                    "outro" -> IntroReviewSegmentType.OUTRO
                    else -> IntroReviewSegmentType.POST_CREDITS
                }, startMs = proposal.startMs, endMs = proposal.endMs, durationMs = durationMs
            )
        }
        com.nuvio.tv.core.diagnostics.YpCrashDiagnostics.logDetectionEvent("review_offered", "proposals", mapOf("proposal_count" to segments.size.toLong()))
        _state.value = IntroReviewState(segments)
        player._uiState.update { it.copy(introReviewPending = true, postPlayMode = null) }
        segments.forEach { loadImages(it.id) }
    }

    fun select(id: String, selected: Boolean) {
        if (_state.value.submitting) return
        _state.update { state -> state.copy(segments = state.segments.map { if (it.id == id) it.copy(selected = selected) else it }) }
    }

    fun adjust(id: String, start: Boolean, delta: Long) {
        if (_state.value.submitting) return
        _state.update { state -> state.copy(segments = state.segments.map { if (it.id == id) it.adjusted(start, delta) else it }) }
        loadImages(id)
    }

    private fun loadImages(id: String) {
        jobs[id]?.cancel()
        val segment = _state.value.segments.find { it.id == id } ?: return
        val media = identity
        val source = url
        val sourceHeaders = headers
        jobs[id] = scope.launch {
            try {
                val start = segment.startMs
                val end = minOf(segment.endMs, segment.durationMs - 1)
                val frames = VideoEvidenceSampler.sample(source, sourceHeaders, segment.durationMs, false, false,
                    longArrayOf(start, end).distinct().toLongArray())
                if (media != identity || profiles.activeProfileId.value != player.profileId) return@launch
                fun bitmap(time: Long) = frames.find { it.requestedTimeMs == time }?.jpeg?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                }
                val startFrame = bitmap(start)
                val endFrame = bitmap(end)
                _state.update { state -> state.copy(segments = state.segments.map {
                    if (it.id == id && it.startMs == segment.startMs && it.endMs == segment.endMs)
                        it.copy(startFrame = startFrame, endFrame = endFrame) else it
                }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Missing images keep approval disabled; user can dismiss. */ }
        }
    }

    fun submit(selected: List<IntroReviewSegment>) {
        if (_state.value.submitting || _state.value.failed || selected.isEmpty() || !player.uiState.value.playbackEnded) return
        if (selected.any { candidate -> _state.value.segments.none { it == candidate } ||
                !candidate.selected || candidate.startFrame == null || candidate.endFrame == null }) return
        val media = identity
        val video = player.currentVideoId ?: player.contentId ?: return
        val imdb = video.substringBefore(':')
        val season = player.currentSeason
        val episode = player.currentEpisode
        val movie = player.contentType.equals("movie", true)
        _state.update { it.copy(submitting = true, failed = false) }
        submissionJob = scope.launch {
            try {
                val key = requireNotNull(store.introDbKey(player.profileId))
                for (segment in selected) {
                    check(media == identity && profiles.activeProfileId.value == player.profileId && store.enabled(player.profileId))
                    // Fetch again per segment: other viewers may have contributed while this episode played.
                    val response = withTimeout(12_000) { api.getSegments(imdb, if (movie) null else season,
                        if (movie) null else episode, true.takeIf { movie }) }
                    check(response.isSuccessful || response.code() == 404)
                    check(!response.isSuccessful || response.body() != null)
                    val data = response.body()
                    val type = when (segment.type) {
                        IntroReviewSegmentType.INTRO -> "intro"
                        IntroReviewSegmentType.RECAP -> "recap"
                        IntroReviewSegmentType.OUTRO -> "outro"
                        IntroReviewSegmentType.POST_CREDITS -> "post-credits"
                    }
                    val exists = when (type) {
                        "intro" -> data?.intro != null || data?.noIntro == true
                        "recap" -> data?.recap != null || data?.noRecap == true
                        "outro" -> data?.outro != null || data?.noOutro == true
                        else -> data?.postCredits != null
                    }
                    if (!exists) {
                        val validated = AiSegmentProposal(type, segment.startMs, segment.endMs, segment.durationMs)
                        check(type != "intro" || segment.endMs - segment.startMs <= 180000)
                        check(attempted.add(segment.id)) // Never retry an ambiguous POST within this session.
                        IntroDbPublisher.submit(key, imdb, season, episode, movie, validated)
                    }
                    _state.update { it.copy(segments = it.segments.filterNot { item -> item.id == segment.id }) }
                }
                _state.update { it.copy(submitting = false) }
                dismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (media == identity) _state.update { it.copy(submitting = false, failed = true) } }
        }
    }
}
