package com.nuvio.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryPreferencesTest {
    @Test fun `parallel and repeated metadata reads share one datastore subscription`() = runTest {
        val profile = MutableStateFlow(1)
        val values = MutableStateFlow(preferencesOf(stringPreferencesKey("tmdb_key") to "key-one"))
        var subscriptions = 0
        val store = mockk<DataStore<Preferences>> {
            every { data } returns flow { subscriptions++; emitAll(values) }
        }
        val factory = mockk<ProfileDataStoreFactory> {
            every { get(1, "content_discovery") } returns store
        }
        val profiles = mockk<ProfileManager> { every { activeProfileId } returns profile }
        val prefs = DiscoveryPreferences(factory, profiles, backgroundScope)

        assertEquals(List(20) { "key-one" }, List(20) { async { prefs.tmdbKey() } }.awaitAll())
        assertEquals("key-one", prefs.tmdbKey())
        assertEquals(1, subscriptions)
        values.value = preferencesOf(stringPreferencesKey("tmdb_key") to "updated")
        runCurrent()
        assertEquals("updated", prefs.tmdbKey())
        assertEquals(1, subscriptions)
    }

    @Test fun `switching profile never returns the previous profiles cached key`() = runTest {
        val profile = MutableStateFlow(1)
        val factory = mockk<ProfileDataStoreFactory>()
        for (id in 1..2) {
            val store = mockk<DataStore<Preferences>> {
                every { data } returns MutableStateFlow(preferencesOf(stringPreferencesKey("tmdb_key") to "key-$id"))
            }
            every { factory.get(id, "content_discovery") } returns store
        }
        val profiles = mockk<ProfileManager> { every { activeProfileId } returns profile }
        val prefs = DiscoveryPreferences(factory, profiles, backgroundScope)
        assertEquals("key-1", prefs.tmdbKey())
        profile.value = 2
        assertEquals("key-2", prefs.tmdbKey())
    }
}
