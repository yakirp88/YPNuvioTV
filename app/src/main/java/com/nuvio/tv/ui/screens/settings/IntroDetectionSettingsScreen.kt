@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.intro.IntroSubmissionMode
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun IntroDetectionSettingsContent(
    initialFocusRequester: FocusRequester,
    viewModel: IntroDetectionSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingAi by remember { mutableStateOf<Boolean?>(null) }
    var dialogProfile by remember { mutableStateOf(-1) }
    var choosingMode by remember { mutableStateOf(false) }
    var showingDiagnostics by remember { mutableStateOf(false) }
    LaunchedEffect(state.profileId) { editingAi = null; choosingMode = false }
    val snapshot = state.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsDetailHeader(
            title = stringResource(R.string.yp_intro_title),
            subtitle = stringResource(R.string.yp_intro_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SettingsActionRow(
                        title = stringResource(R.string.yp_diagnostics_title),
                        subtitle = stringResource(R.string.yp_diagnostics_description),
                        onClick = { showingDiagnostics = true }
                    )
                }
                item {
                    SettingsToggleRow(
                        title = stringResource(R.string.yp_intro_collect),
                        subtitle = stringResource(R.string.yp_intro_collect_description),
                        checked = snapshot?.enabled == true,
                        onToggle = { if (!state.loading && !state.saving) viewModel.setEnabled(snapshot?.enabled != true) },
                        modifier = Modifier.focusRequester(initialFocusRequester)
                    )
                }
                item {
                    SettingsActionRow(
                        title = stringResource(R.string.yp_intro_submission_mode),
                        subtitle = stringResource(R.string.yp_intro_submission_description),
                        value = stringResource(
                            if (snapshot?.submissionMode == IntroSubmissionMode.AUTOMATIC)
                                R.string.yp_intro_mode_auto else R.string.yp_intro_mode_review
                        ),
                        enabled = !state.loading && !state.saving,
                        onClick = { choosingMode = true }
                    )
                }
                item {
                    SettingsToggleRow(
                        title = stringResource(R.string.yp_intro_cloud),
                        subtitle = stringResource(R.string.yp_intro_cloud_description),
                        checked = snapshot?.cloudEnabled == true,
                        onToggle = { if (!state.loading && !state.saving) viewModel.setCloudEnabled(snapshot?.cloudEnabled != true) }
                    )
                }
                item {
                    Text(
                        stringResource(R.string.yp_intro_phase_notice),
                        color = NuvioTheme.colors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                item {
                    SettingsActionRow(
                        title = stringResource(R.string.yp_intro_ai_key),
                        subtitle = stringResource(R.string.yp_intro_key_description),
                        value = stringResource(if (snapshot?.hasAiKey == true) R.string.yp_intro_key_saved else R.string.mdblist_not_set),
                        enabled = !state.loading && !state.saving,
                        onClick = { dialogProfile = state.profileId; editingAi = true }
                    )
                }
                item {
                    SettingsActionRow(
                        title = stringResource(R.string.yp_intro_db_key),
                        subtitle = stringResource(R.string.yp_intro_key_description),
                        value = stringResource(if (snapshot?.hasIntroDbKey == true) R.string.yp_intro_key_saved else R.string.mdblist_not_set),
                        enabled = !state.loading && !state.saving,
                        onClick = { dialogProfile = state.profileId; editingAi = false }
                    )
                }
                item {
                    Text(
                        if (snapshot?.latestHintCount == null) stringResource(R.string.yp_intro_no_evidence)
                        else stringResource(R.string.yp_intro_evidence_count, snapshot.latestHintCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                if (!snapshot?.latestEvidence.isNullOrBlank()) {
                    item { Text(snapshot!!.latestEvidence, modifier = Modifier.padding(horizontal = 12.dp)) }
                }
                if (state.error) {
                    item { Text(stringResource(R.string.yp_intro_storage_error), modifier = Modifier.padding(12.dp)) }
                }
            }
        }
    }
    if (choosingMode) {
        NuvioDialog(
            onDismiss = { choosingMode = false },
            title = stringResource(R.string.yp_intro_submission_mode),
            width = 700.dp
        ) {
            SettingsActionRow(
                title = stringResource(R.string.yp_intro_mode_review),
                subtitle = stringResource(R.string.yp_intro_mode_review_description),
                onClick = {
                    viewModel.setSubmissionMode(IntroSubmissionMode.REVIEW_AT_EPISODE_END)
                    choosingMode = false
                }
            )
            SettingsActionRow(
                title = stringResource(R.string.yp_intro_mode_auto),
                subtitle = stringResource(R.string.yp_intro_mode_auto_description),
                onClick = {
                    viewModel.setSubmissionMode(IntroSubmissionMode.AUTOMATIC)
                    choosingMode = false
                }
            )
        }
    }
    if (showingDiagnostics) YpCrashDiagnosticsDialog(onDismiss = { showingDiagnostics = false })
    editingAi?.let { ai ->
        IntroDetectionKeyDialog(
            ai = ai,
            saving = state.saving,
            error = state.error,
            onDismiss = { editingAi = null },
            onSave = { value -> viewModel.saveKey(dialogProfile, ai, value) { editingAi = null } }
        )
    }
}

@Composable
private fun IntroDetectionKeyDialog(
    ai: Boolean,
    saving: Boolean,
    error: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    // Deliberately neither rememberSaveable nor prefilled with the existing secret.
    var value by remember(ai) { mutableStateOf("") }
    val inputFocus = remember { FocusRequester() }
    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(if (ai) R.string.yp_intro_ai_key else R.string.yp_intro_db_key),
        subtitle = stringResource(R.string.yp_intro_key_dialog),
        width = 700.dp
    ) {
        Card(onClick = { inputFocus.requestFocus() }, modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 1024) value = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(inputFocus),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = NuvioTheme.colors.TextPrimary,
                    textDirection = TextDirection.Ltr
                ),
                cursorBrush = SolidColor(NuvioTheme.colors.Primary)
            )
        }
        if (error) Text(stringResource(R.string.yp_intro_storage_error))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = { onSave("") }, enabled = !saving) { Text(stringResource(R.string.action_clear)) }
            Button(onClick = { onSave(value) }, enabled = !saving && value.isNotBlank()) {
                Text(stringResource(if (saving) R.string.action_saving else R.string.action_save))
            }
        }
    }
}
