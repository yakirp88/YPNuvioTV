package com.nuvio.tv.ui.screens.search

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
    viewModel: SearchViewModel = hiltViewModel(),
    showBuiltInHeader: Boolean = true,
    onNavigateToDetail: (String, String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    if(state.discoverLocation == DiscoverLocation.OFF) {
        EmptyScreenState(title=stringResource(R.string.discover_disabled_title),
            subtitle=stringResource(R.string.discover_disabled_subtitle),icon=Icons.Default.Search)
    } else com.nuvio.tv.ui.screens.discovery.ContentDiscoveryScreen(onNavigateToDetail)
}
