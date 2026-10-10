package com.nuvio.tv.ui.screens.settings

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R

@Composable
internal fun TmdbApiKeySettingsRow(viewModel: TmdbSettingsViewModel) {
    val context = LocalContext.current
    val configured by viewModel.hasApiKey.collectAsStateWithLifecycle()
    SettingsActionRow(
        title = stringResource(R.string.yp_tmdb_api_key_title),
        subtitle = stringResource(R.string.yp_tmdb_api_key_info),
        value = stringResource(if (configured) R.string.yp_tmdb_api_key_saved else R.string.yp_tmdb_api_key_not_set),
        onClick = { showTmdbApiKeyDialog(context, viewModel::saveApiKey) }
    )
}

/** Match IntroDB's native input dialog without displaying the saved credential. */
private fun showTmdbApiKeyDialog(context: Context, onSave: (String, (Boolean) -> Unit) -> Unit) {
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isSingleLine = true
        hint = "TMDB API key (v3)"
    }
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 12, 32, 12)
        addView(TextView(context).apply { text = context.getString(R.string.yp_tmdb_api_key_info) })
        addView(input)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.yp_tmdb_api_key_title)
        .setView(panel)
        .setPositiveButton(R.string.yp_report_save, null)
        .setNegativeButton(R.string.yp_report_cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val key = input.text.toString().trim()
            if (key.isBlank()) {
                input.error = context.getString(R.string.yp_tmdb_api_key_required)
            } else {
                val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                saveButton.isEnabled = false
                onSave(key) { saved ->
                    saveButton.isEnabled = true
                    if (saved) { input.text.clear(); dialog.dismiss() }
                    else input.error = context.getString(R.string.yp_tmdb_api_key_failed)
                }
            }
        }
    }
    dialog.setOnDismissListener { input.text.clear() }
    dialog.show()
}
