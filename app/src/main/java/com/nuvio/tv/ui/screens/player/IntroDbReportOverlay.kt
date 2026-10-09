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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.data.introdb.IntroDbReportRepository
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

@Composable
internal fun IntroDbReportOverlay(state: IntroDbReportState, coordinator: IntroDbReportCoordinator, onConfigureKey: () -> Unit) {
    var adjustmentStep by remember(state.stage) { mutableStateOf(1000L) }
    val firstFocus = remember(state.stage) { FocusRequester() }
    var initialFocusPlaced by remember(state.stage, state.recordingPlayerControls) { mutableStateOf(false) }
    LaunchedEffect(state.stage, state.previewing, state.recordingPlayerControls) {
        if (state.active && !state.recordingPlayerControls && !initialFocusPlaced &&
            !(state.stage == ReportStage.REVIEW && state.previewing)) {
            firstFocus.requestFocusAfterFrames()
            initialFocusPlaced = true
        }
    }
    when (state.stage) {
        ReportStage.IDLE -> Unit
        ReportStage.CHOOSE -> Dialog(onDismissRequest = coordinator::cancel) {
            Column(Modifier.width(420.dp).background(Color(0xFF18202E), RoundedCornerShape(24.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.yp_report_choose), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.yp_report_saved_start, reportTime(state.startMs)))
                state.available.sortedBy { it.ordinal }.forEachIndexed { index, segment ->
                    Button(onClick = { coordinator.choose(segment) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier) {
                        Text(stringResource(segment.labelResource()))
                    }
                }
                if (coordinator.hasDraft()) {
                    Button(onClick = coordinator::resumeDraft) { Text(stringResource(R.string.yp_report_resume_draft)) }
                }
                Button(onClick = coordinator::cancel) { Text(stringResource(R.string.yp_report_cancel)) }
            }
        }
        ReportStage.RECORDING -> {
            // Status only: this indicator never takes focus from playback controls.
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
            if (!state.recordingPlayerControls) {
                Dialog(onDismissRequest = coordinator::showPlayerControls) {
                    Column(Modifier.width(340.dp).background(Color(0xFF18202E), RoundedCornerShape(24.dp)).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(state.segment!!.labelResource()), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = coordinator::finish, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) {
                            Text(stringResource(R.string.yp_report_finish))
                        }
                        Button(onClick = coordinator::cancel, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.yp_report_cancel))
                        }
                        ReportMessage(state.message)
                    }
                }
            }
        }
        ReportStage.REVIEW -> Dialog(onDismissRequest = coordinator::cancel,
            properties = DialogProperties(usePlatformDefaultWidth = false,
                dismissOnBackPress = !state.sending, dismissOnClickOutside = false)) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(0.85f)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(160).dp)
                .verticalScroll(rememberScrollState())
                .background(Color(0xFF18202E), RoundedCornerShape(24.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.yp_report_review), style = MaterialTheme.typography.titleLarge)
                val media = state.media!!
                Text(stringResource(state.segment!!.labelResource()) + " · " + media.imdbId +
                    if (media.movie) "" else " · S${media.season} E${media.episode}",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReportBoundary(state, true, coordinator, Modifier.weight(1f), firstFocus, adjustmentStep)
                    ReportBoundary(state, false, coordinator, Modifier.weight(1f), null, adjustmentStep)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(listOf(1000L, 5000L, 10000L), listOf(60000L, 300000L, 600000L)).forEach { steps ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            steps.forEach { step ->
                                Button(onClick = { adjustmentStep = step }, enabled = !state.sending,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text((if (adjustmentStep == step) "• " else "") + adjustmentLabel(step))
                                }
                            }
                        }
                    }
                }
                if (state.previewing) Text(stringResource(R.string.yp_report_loading_frame))
                ReportMessage(state.message)
                if (state.message == "key_failed") {
                    Button(onClick = onConfigureKey) { Text(stringResource(R.string.yp_report_key_title)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = coordinator::send, enabled = !state.sending && !state.previewing) {
                        Text(stringResource(if (state.sending) R.string.yp_report_sending else R.string.yp_report_send))
                    }
                    if (state.message != null) {
                        Button(onClick = coordinator::keepDraft, enabled = !state.sending) {
                            Text(stringResource(R.string.yp_report_keep_draft))
                        }
                    }
                    Button(onClick = coordinator::cancel, enabled = !state.sending) {
                        Text(stringResource(R.string.yp_report_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportBoundary(state: IntroDbReportState, start: Boolean, coordinator: IntroDbReportCoordinator,
    modifier: Modifier, focus: FocusRequester?, step: Long) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(if (start) R.string.yp_report_start else R.string.yp_report_end) + ": " +
            reportTime(if (start) state.startMs else state.endMs))
        val bitmap = if (start) state.startImage else state.endImage
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Color.Black), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.yp_report_frame), Modifier.fillMaxSize())
            else Text(stringResource(if (state.previewing) R.string.yp_report_loading_frame else R.string.yp_report_frame_unavailable),
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-step, step).forEachIndexed { index, delta ->
                Button(onClick = { coordinator.adjust(start, delta) }, enabled = !state.sending && !state.previewing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).then(if (index == 0 && focus != null) Modifier.focusRequester(focus) else Modifier)) {
                    Text((if (delta < 0) "− " else "+ ") + adjustmentLabel(step))
                }
            }
        }
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

@Composable
private fun adjustmentLabel(step: Long): String = if (step >= 60000L)
    stringResource(R.string.yp_report_minutes, (step / 60000L).toInt())
    else "${step / 1000}s"
