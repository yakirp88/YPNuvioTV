@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.intro.SegmentReviewRange
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.detail.formatPlaybackTime
import com.nuvio.tv.ui.theme.NuvioTheme

enum class IntroReviewSegmentType { INTRO, RECAP, OUTRO, POST_CREDITS }

/** Model proposals with boundary images for human verification; never raw subtitle hints. */
data class IntroReviewSegment(
    val id: String,
    val type: IntroReviewSegmentType,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val startFrame: ImageBitmap? = null,
    val endFrame: ImageBitmap? = null,
    val selected: Boolean = true
) {
    init {
        require(id.isNotBlank())
        SegmentReviewRange(startMs, endMs, durationMs)
    }

    /** A time change invalidates its image; callers must extract a fresh frame at the new time. */
    fun adjusted(start: Boolean, deltaMs: Long): IntroReviewSegment {
        val adjusted = SegmentReviewRange(startMs, endMs, durationMs).adjusted(start, deltaMs)
        return if (start) {
            val next = adjusted.startMs
            if (next == startMs) this else copy(startMs = next, startFrame = null)
        } else {
            val next = adjusted.endMs
            if (next == endMs) this else copy(endMs = next, endFrame = null)
        }
    }
}

/**
 * End-of-episode review surface. The owner holds episode navigation until dismiss/success,
 * extracts fresh boundary images after edits, and supplies the authenticated submit callback.
 * This component performs no network requests and cannot promote subtitle clues to segments.
 */
@Composable
fun IntroSegmentReviewDialog(
    segments: List<IntroReviewSegment>,
    submitting: Boolean,
    submissionFailed: Boolean,
    onSelectionChange: (String, Boolean) -> Unit,
    onAdjustBoundary: (String, Boolean, Long) -> Unit,
    onApproveAndSubmit: (List<IntroReviewSegment>) -> Unit,
    onDismiss: () -> Unit
) {
    require(segments.isNotEmpty())
    require(segments.map { it.id }.distinct().size == segments.size)
    val dismissFocus = remember { FocusRequester() }
    val maxListHeight = (LocalConfiguration.current.screenHeightDp - 300).coerceAtLeast(120).dp
    LaunchedEffect(Unit) { dismissFocus.requestFocus() }
    NuvioDialog(
        onDismiss = { if (!submitting) onDismiss() },
        title = stringResource(R.string.yp_intro_review_title),
        subtitle = stringResource(R.string.yp_intro_review_description),
        width = 900.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxListHeight),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(segments, key = { it.id }) { segment ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onSelectionChange(segment.id, !segment.selected) },
                            enabled = !submitting
                        ) { Text(stringResource(if (segment.selected) R.string.yp_intro_included else R.string.yp_intro_excluded)) }
                        Text(stringResource(when (segment.type) {
                            IntroReviewSegmentType.INTRO -> R.string.yp_intro_type_intro
                            IntroReviewSegmentType.RECAP -> R.string.yp_intro_type_recap
                            IntroReviewSegmentType.OUTRO -> R.string.yp_intro_type_outro
                            IntroReviewSegmentType.POST_CREDITS -> R.string.yp_intro_type_postcredits
                        }))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BoundaryPreview(
                            image = segment.startFrame,
                            timeMs = segment.startMs,
                            start = true,
                            enabled = !submitting,
                            onAdjust = { delta -> onAdjustBoundary(segment.id, true, delta) },
                            modifier = Modifier.weight(1f)
                        )
                        BoundaryPreview(
                            image = segment.endFrame,
                            timeMs = segment.endMs,
                            start = false,
                            enabled = !submitting,
                            onAdjust = { delta -> onAdjustBoundary(segment.id, false, delta) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        if (submissionFailed) Text(stringResource(R.string.yp_intro_review_failed))
        val selected = segments.filter { it.selected }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onDismiss,
                enabled = !submitting,
                modifier = Modifier.focusRequester(dismissFocus)
            ) { Text(stringResource(R.string.yp_intro_review_skip)) }
            Button(
                onClick = { onApproveAndSubmit(selected) },
                enabled = !submitting && !submissionFailed && selected.isNotEmpty() && selected.all {
                    it.startFrame != null && it.endFrame != null
                }
            ) {
                Text(stringResource(if (submitting) R.string.yp_intro_review_sending else R.string.yp_intro_review_submit))
            }
        }
    }
}

@Composable
private fun BoundaryPreview(
    image: ImageBitmap?,
    timeMs: Long,
    start: Boolean,
    enabled: Boolean,
    onAdjust: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(if (start) R.string.yp_intro_boundary_start else R.string.yp_intro_boundary_end)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label · ${formatPlaybackTime(timeMs)}")
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(NuvioTheme.colors.BackgroundElevated),
            contentAlignment = Alignment.Center
        ) {
            if (image == null) Text(stringResource(R.string.yp_intro_preview_pending))
            else Image(image, contentDescription = "$label · ${formatPlaybackTime(timeMs)}", contentScale = ContentScale.Fit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAdjust(-1000) }, enabled = enabled) { Text("−1s") }
            Button(onClick = { onAdjust(1000) }, enabled = enabled) { Text("+1s") }
        }
    }
}
