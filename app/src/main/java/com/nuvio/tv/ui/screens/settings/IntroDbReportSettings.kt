package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.data.introdb.IntroDbReportRepository
import com.nuvio.tv.ui.screens.player.showIntroDbKeyDialog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IntroDbReportSettingsEntryPoint { fun introDbReportRepository(): IntroDbReportRepository }

@Composable
internal fun IntroDbReportSettingsRow() {
    val context = LocalContext.current
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, IntroDbReportSettingsEntryPoint::class.java)
            .introDbReportRepository()
    }
    var enabled by remember { mutableStateOf(repository.enabled) }
    SettingsToggleRow(title = stringResource(R.string.yp_report_enabled),
        subtitle = stringResource(R.string.yp_report_enabled_info), checked = enabled,
        onToggle = { enabled = !enabled; repository.enabled = enabled })
    SettingsActionRow(title = stringResource(R.string.yp_report_key_title),
        subtitle = stringResource(R.string.yp_report_key_info), value = null,
        onClick = { showIntroDbKeyDialog(context, repository) })
}
