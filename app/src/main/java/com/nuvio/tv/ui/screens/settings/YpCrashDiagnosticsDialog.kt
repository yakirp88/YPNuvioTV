@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.diagnostics.YpCrashDiagnostics
import com.nuvio.tv.core.diagnostics.YpCrashReportText
import com.nuvio.tv.ui.components.NuvioDialog

@Composable
internal fun YpCrashRecoveryPrompt() {
    val pending by YpCrashDiagnostics.pendingCrash.collectAsState()
    if (pending != null) YpCrashDiagnosticsDialog(onDismiss = YpCrashDiagnostics::acknowledge)
}

@Composable
internal fun YpCrashDiagnosticsDialog(onDismiss: () -> Unit) {
    var report by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        report = try { YpCrashDiagnostics.report() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { "Diagnostic storage unavailable" }
    }
    val pages = remember(report) { YpCrashReportText.pages(report ?: "Loading diagnostics...") }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.yp_diagnostics_title),
        subtitle = stringResource(R.string.yp_diagnostics_description),
        width = 920.dp,
        contentSpacing = 10.dp,
        contentPadding = 16.dp
    ) {
        Text("${page + 1} / ${pages.size}", fontSize = 16.sp)
        Text(pages[page.coerceIn(pages.indices)], modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 19.sp,
            style = androidx.tv.material3.MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { page-- }, enabled = page > 0) { Text(stringResource(R.string.yp_diagnostics_previous)) }
            Button(onClick = { page++ }, enabled = page < pages.lastIndex) { Text(stringResource(R.string.yp_diagnostics_next)) }
            Button(onClick = { report?.let { clipboard.setText(AnnotatedString(it)); copied = true } }, enabled = report != null) {
                Text(stringResource(if (copied) R.string.yp_diagnostics_copied else R.string.yp_diagnostics_copy))
            }
            Button(onClick = onDismiss) { Text(stringResource(R.string.yp_diagnostics_close)) }
        }
    }
}
