@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.player

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.data.introdb.IntroDbReportRepository
import com.nuvio.tv.data.introdb.IntroDbReportRules
import com.nuvio.tv.data.introdb.ReportSegment
import java.util.Locale

internal fun ReportSegment.labelResource(): Int = when (this) {
    ReportSegment.INTRO -> R.string.yp_report_intro
    ReportSegment.RECAP -> R.string.yp_report_recap
    ReportSegment.OUTRO -> R.string.yp_report_outro
}

/** Input stays on-device; never puts a credential in an Intent, URL, or logging call. */
internal fun showIntroDbKeyDialog(context: Context, repository: IntroDbReportRepository, onSaved: () -> Unit = {}) {
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isSingleLine = true
        hint = "idb_…"
    }
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 12, 32, 12)
        addView(TextView(context).apply { text = context.getString(R.string.yp_report_key_info) })
        addView(input)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.yp_report_key_title).setView(panel)
        .setPositiveButton(R.string.yp_report_save, null)
        .setNegativeButton(R.string.yp_report_cancel, null)
        .setNeutralButton(R.string.yp_report_get_key) { _, _ ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://introdb.app/"))) }
                .onFailure { Toast.makeText(context, "https://introdb.app/", Toast.LENGTH_LONG).show() }
        }.create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val saved = runCatching { repository.saveApiKey(input.text.toString()) }.isSuccess
            if (saved) { input.text.clear(); dialog.dismiss(); onSaved() }
            else input.error = context.getString(R.string.yp_report_key_failed)
        }
    }
    dialog.setOnDismissListener { input.text.clear() }
    dialog.show()
}

/** This popup is composed inside the flag's Box, so it follows the real button bounds. */
@Composable
internal fun IntroDbReportMenu(state: IntroDbReportState, coordinator: IntroDbReportCoordinator) {
    val open = state.stage == ReportStage.CHOOSE ||
        (state.stage == ReportStage.RECORDING && !state.recordingPlayerControls)
    if (!open) return
    val first = remember(state.stage) { FocusRequester() }
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    val position = remember(gap) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
            return IntOffset(x, (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(gap))
        }
    } }
    Popup(popupPositionProvider = position,
        onDismissRequest = { if (state.stage == ReportStage.CHOOSE) coordinator.cancel() else coordinator.showPlayerControls() },
        properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(210.dp).background(Color(0xD9101722), RoundedCornerShape(16.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.stage == ReportStage.CHOOSE) {
                state.available.sortedBy { it.ordinal }.forEachIndexed { index, segment ->
                    Button(onClick = { coordinator.choose(segment) }, contentPadding = PaddingValues(10.dp, 6.dp),
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier)) {
                        Text(stringResource(segment.labelResource()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (coordinator.hasDraft()) Button(onClick = coordinator::resumeDraft) {
                    Text(stringResource(R.string.yp_report_resume_draft), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Button(onClick = coordinator::finish, modifier = Modifier.fillMaxWidth().focusRequester(first)) {
                    Text(stringResource(R.string.yp_report_finish), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = coordinator::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.yp_report_cancel), style = MaterialTheme.typography.bodyMedium)
                }
                ReportMessage(state.message)
            }
        }
        LaunchedEffect(Unit) { first.requestFocusAfterFrames() }
    }
}

@Composable
internal fun IntroDbReportOverlay(state: IntroDbReportState, coordinator: IntroDbReportCoordinator, onConfigureKey: () -> Unit) {
    if (state.stage == ReportStage.RECORDING) {
        val transition = rememberInfiniteTransition(label = "report activity")
        val rotation by transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "report ring")
        val description = stringResource(R.string.yp_report_return)
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopStart).padding(28.dp).size(40.dp)
                .background(Color(0xD918202E), CircleShape)
                .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                    drawCircle(Color.White.copy(alpha = 0.18f), style = Stroke(2.dp.toPx()))
                    drawArc(Color(0xFF8FC9FF), rotation, 100f, false, style = Stroke(2.dp.toPx()))
                }
                Icon(Icons.Default.Flag, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
    if (state.stage == ReportStage.REVIEW) IntroDbReview(state, coordinator, onConfigureKey)
}

@Composable
private fun IntroDbReview(state: IntroDbReportState, coordinator: IntroDbReportCoordinator, onConfigureKey: () -> Unit) {
    val startFocus = remember { FocusRequester() }
    val endFocus = remember { FocusRequester() }
    val calibrationFocus = remember { FocusRequester() }
    var placed by remember { mutableStateOf(false) }
    var previousSelection by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.previewing, state.calibratingStart) {
        val selected = state.calibratingStart
        if (selected != null) {
            if (previousSelection != selected) calibrationFocus.requestFocusAfterFrames()
            previousSelection = selected
        } else if (previousSelection != null) {
            (if (previousSelection == true) startFocus else endFocus).requestFocusAfterFrames()
            previousSelection = null
        } else if (!placed && !state.previewing) {
            startFocus.requestFocusAfterFrames(); placed = true
        }
    }
    Popup(alignment = Alignment.Center,
        onDismissRequest = { if (state.calibratingStart != null) coordinator.endCalibration() else coordinator.cancel() },
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp).widthIn(max = 520.dp).fillMaxWidth(0.7f)
                .background(Color(0xBB101722), RoundedCornerShape(20.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BoundaryImage(state, true, coordinator, startFocus, Modifier.weight(1f))
                    BoundaryImage(state, false, coordinator, endFocus, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = coordinator::cancel, enabled = !state.sending, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.yp_report_cancel))
                    }
                    Button(onClick = coordinator::send, enabled = !state.sending && !state.previewing, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (state.sending) R.string.yp_report_sending else R.string.yp_report_send))
                    }
                }
                // Error recovery appears only when needed; credentials are never shown here.
                ReportMessage(state.message)
                if (state.message == "key_failed") Button(onClick = onConfigureKey) {
                    Text(stringResource(R.string.yp_report_key_title))
                }
                if (state.message != null) Button(onClick = coordinator::keepDraft, enabled = !state.sending) {
                    Text(stringResource(R.string.yp_report_keep_draft))
                }
            }
            state.calibratingStart?.let { start ->
                val timestamp = if (start) state.startMs else state.endMs
                Column(Modifier.align(Alignment.TopCenter).padding(top = 32.dp).widthIn(max = 560.dp).fillMaxWidth(0.65f)
                    .background(Color(0xCC0F0F0F), RoundedCornerShape(26.dp)).padding(22.dp)
                    .focusRequester(calibrationFocus)
                    .onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        val direction = key.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT || key.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                        if (direction) {
                            if (key.action == AndroidKeyEvent.ACTION_DOWN) coordinator.adjust(start,
                                IntroDbReportRules.calibrationDelta(key.repeatCount, key.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT))
                            true
                        } else if (key.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER || key.keyCode == AndroidKeyEvent.KEYCODE_ENTER) {
                            if (key.action == AndroidKeyEvent.ACTION_UP) coordinator.endCalibration()
                            true
                        } else false
                    }.focusable(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (start) R.string.yp_report_calibrate_start else R.string.yp_report_calibrate_end),
                        style = MaterialTheme.typography.titleMedium)
                    Text(reportTime(timestamp), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("‹")
                        Canvas(Modifier.weight(1f).height(26.dp)) {
                            val cy = size.height / 2
                            drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, cy), Offset(size.width, cy), 2.dp.toPx())
                            repeat(11) { i ->
                                val x = size.width * i / 10
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, cy - 4.dp.toPx()), Offset(x, cy + 4.dp.toPx()), 1.dp.toPx())
                            }
                            val fraction = (timestamp.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                            drawCircle(Color(0xFF4AA3FF), 5.dp.toPx(), Offset(size.width * fraction, cy))
                        }
                        Text("›")
                    }
                    Text(stringResource(R.string.yp_report_calibration_hint), style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f))
                    if (state.previewing) Text(stringResource(R.string.yp_report_loading_frame), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BoundaryImage(state: IntroDbReportState, start: Boolean, coordinator: IntroDbReportCoordinator,
    focus: FocusRequester, modifier: Modifier) {
    val bitmap = if (start) state.startImage else state.endImage
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { coordinator.selectBoundary(start) }, enabled = !state.sending && !state.previewing,
            contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9).focusRequester(focus)) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.yp_report_frame), Modifier.fillMaxSize())
                else Text(stringResource(if (state.previewing) R.string.yp_report_loading_frame else R.string.yp_report_frame_unavailable),
                    modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(if (start) R.string.yp_report_start else R.string.yp_report_end), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ReportMessage(message: String?) {
    val resource = when (message) {
        "sent" -> R.string.yp_report_sent
        "exists" -> R.string.yp_report_exists
        "check_failed" -> R.string.yp_report_check_failed
        "key_failed" -> R.string.yp_report_key_failed
        "rate_limit" -> R.string.yp_report_rate_limit
        "invalid_range", "rejected" -> R.string.yp_report_invalid_range
        "send_failed" -> R.string.yp_report_send_failed
        else -> null
    }
    if (resource != null) Text(stringResource(resource))
}

internal fun reportTime(ms: Long): String = String.format(Locale.ROOT, "%02d:%02d:%02d.%03d",
    ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
