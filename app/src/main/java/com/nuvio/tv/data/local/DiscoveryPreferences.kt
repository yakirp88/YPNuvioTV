package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryPreferences @Inject constructor(private val factory: ProfileDataStoreFactory, private val profiles: ProfileManager) {
    private fun store() = factory.get(profiles.activeProfileId.value, "content_discovery")
    val profilePreferences = profiles.activeProfileId.flatMapLatest { id -> factory.get(id, "content_discovery").data.map { id to it } }
    val preferences = profilePreferences.map { it.second }
    val includeMissing = preferences.map { it[booleanPreferencesKey("include_missing")] ?: false }
    val languages = preferences.map { p -> listOf("primary", "secondary", "tertiary").mapIndexed { i, key -> p[stringPreferencesKey(key)] ?: listOf("he", "en", "original")[i] } }
    suspend fun setIncludeMissing(value: Boolean) { store().edit { it[booleanPreferencesKey("include_missing")] = value } }
    suspend fun setLanguage(index: Int, value: String) { store().edit { it[stringPreferencesKey(listOf("primary", "secondary", "tertiary")[index])] = value } }
    suspend fun save(key: String, value: String) { store().edit { it[stringPreferencesKey(key)] = value } }
}
