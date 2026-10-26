@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog

@Composable
internal fun IntroDetectionSummaryDialog(status: String, onDismiss: () -> Unit) {
    val message = when (status) {
        "waiting", "lookup", "subtitles", "cloud", "media" -> R.string.yp_detection_running
        "already_reported" -> R.string.yp_detection_existing
        "missing_metadata" -> R.string.yp_detection_metadata
        "lookup_failed" -> R.string.yp_detection_lookup_failed
        "automatic_unavailable" -> R.string.yp_detection_automatic_unavailable
        "cloud_disabled" -> R.string.yp_detection_cloud_disabled
        "no_evidence" -> R.string.yp_detection_no_evidence
        "no_proposals", "proposals" -> R.string.yp_detection_no_proposals
        else -> R.string.yp_detection_failed
    }
    NuvioDialog(onDismiss = onDismiss, title = stringResource(R.string.yp_detection_summary_title)) {
        Text(stringResource(message))
        Text(stringResource(R.string.yp_detection_status_code, status))
        Text(stringResource(R.string.yp_detection_diagnostic_hint))
        Button(onClick = onDismiss) { Text(stringResource(R.string.yp_diagnostics_close)) }
    }
}
