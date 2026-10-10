package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryPreferences internal constructor(private val factory: ProfileDataStoreFactory, private val profiles: ProfileManager, scope: CoroutineScope) {
    @Inject constructor(factory: ProfileDataStoreFactory, profiles: ProfileManager) :
        this(factory, profiles, CoroutineScope(SupervisorJob() + Dispatchers.IO))
    private fun store() = factory.get(profiles.activeProfileId.value, "content_discovery")
    // Metadata requests fan out across every home row. Keep one subscription instead
    // of restarting the profile/DataStore flow for each HTTP request.
    val profilePreferences = profiles.activeProfileId.flatMapLatest { id -> factory.get(id, "content_discovery").data.map { id to it } }
        .shareIn(scope, SharingStarted.Lazily, replay = 1)
    val preferences = profilePreferences.map { it.second }
    suspend fun tmdbKey(): String? = profilePreferences.first { it.first == profiles.activeProfileId.value }
        .second[stringPreferencesKey("tmdb_key")]?.takeIf { it.isNotBlank() }
    val infoPosition = preferences.map { com.nuvio.tv.ui.screens.discovery.discoveryInfoPosition(it[stringPreferencesKey("info_position")]) }
    val expansionDelay = preferences.map { com.nuvio.tv.ui.screens.discovery.discoveryExpansionDelay(it[stringPreferencesKey("expansion_delay")]) }
    suspend fun setInfoPosition(value: com.nuvio.tv.ui.screens.discovery.DiscoveryInfoPosition) = save("info_position",value.name)
    suspend fun setExpansionDelay(value: Int) = save("expansion_delay",value.coerceIn(0,10).toString())
    val includeMissing = preferences.map { it[booleanPreferencesKey("include_missing")] ?: false }
    val languages = preferences.map { p -> listOf("primary", "secondary", "tertiary").mapIndexed { i, key -> p[stringPreferencesKey(key)] ?: listOf("he", "en", "original")[i] } }
    suspend fun setIncludeMissing(value: Boolean) { store().edit { it[booleanPreferencesKey("include_missing")] = value } }
    suspend fun setLanguage(index: Int, value: String) { store().edit { it[stringPreferencesKey(listOf("primary", "secondary", "tertiary")[index])] = value } }
    suspend fun save(key: String, value: String) { store().edit { it[stringPreferencesKey(key)] = value } }
}
