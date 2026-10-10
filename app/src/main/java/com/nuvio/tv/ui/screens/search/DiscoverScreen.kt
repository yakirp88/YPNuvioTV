package com.nuvio.tv.ui.screens.search

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.ui.components.EmptyScreenState

@Composable
fun DiscoverScreen(
    viewModel: DiscoveryEntryViewModel = hiltViewModel(),
    showBuiltInHeader: Boolean = true,
    onNavigateToDetail: (String, String, String) -> Unit
) {
    val state by viewModel.location.collectAsState()
    if(state == DiscoverLocation.OFF) {
        EmptyScreenState(title=stringResource(R.string.discover_disabled_title),
            subtitle=stringResource(R.string.discover_disabled_subtitle),icon=Icons.Default.Search)
    } else com.nuvio.tv.ui.screens.discovery.ContentDiscoveryScreen(onNavigateToDetail)
}

@dagger.hilt.android.lifecycle.HiltViewModel
class DiscoveryEntryViewModel @javax.inject.Inject constructor(layout:com.nuvio.tv.data.local.LayoutPreferenceDataStore):androidx.lifecycle.ViewModel() {
    val location=layout.discoverLocation.stateIn(viewModelScope,kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),DiscoverLocation.OFF)
}
