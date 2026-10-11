package com.nuvio.tv.data.local

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiscoveryMetadataCacheTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun quotaCountsActualFilesAndEvictsLeastRecentlyReadEntry() {
        var time=1000L
        val folder=temporary.newFolder()
        val cache=DiscoveryMetadataDiskCache(folder) {time}
        cache.configure(36)
        cache.write("a","0123456789");time++;cache.write("b","0123456789")
        time++;assertNotNull(cache.read("a",1000));time++;cache.write("c","0123456789")
        assertNull(cache.read("b",1000));assertNotNull(cache.read("a",1000));assertNotNull(cache.read("c",1000))
        assertEquals(36L,cache.usedBytes)
        assertEquals(cache.usedBytes,folder.listFiles()!!.sumOf {it.length()})
        cache.configure(18);assertEquals(18L,cache.usedBytes)
    }
    @Test fun freshnessUsesCreationTimeAndSurvivesRestartInsteadOfExtendingOnEveryRead() {
        var time=1000L
        val folder=temporary.newFolder()
        val cache=DiscoveryMetadataDiskCache(folder) {time}
        cache.write("query","{\"title\":\"example\"}")
        time=1099;assertNotNull(cache.read("query",100))
        val restarted=DiscoveryMetadataDiskCache(folder) {time}
        assertEquals(cache.usedBytes,restarted.usedBytes)
        time=1100;assertNull(restarted.read("query",100));assertEquals(0L,restarted.usedBytes)
    }
    @Test fun clearingInvalidatesInFlightWritesWithoutChangingQuota() {
        val cache=DiscoveryMetadataDiskCache(temporary.newFolder())
        cache.configure(64)
        val ticket=cache.revision
        cache.write("old","metadata",ticket);cache.clear()
        cache.write("late","metadata",ticket)
        assertEquals(0L,cache.usedBytes);assertNull(cache.read("late",1000))
        cache.write("new","metadata",cache.revision);assertNotNull(cache.read("new",1000))
        cache.configure(0);cache.write("disabled","metadata");assertEquals(0L,cache.usedBytes)
    }
    @Test fun oversizedAndCorruptEntriesDoNotBreakOtherTitlesOrExposeCacheKeys() {
        val folder=temporary.newFolder()
        val cache=DiscoveryMetadataDiskCache(folder)
        cache.configure(32);cache.write("too big","x".repeat(33));assertEquals(0L,cache.usedBytes)
        cache.write("profile:api-key:actor=31","{}")
        assertFalse(folder.listFiles()!!.single().name.contains("api-key"))
        folder.listFiles()!!.single().writeText("bad")
        assertNull(cache.read("profile:api-key:actor=31",1000));assertEquals(0L,cache.usedBytes)
    }
    @Test fun sliderIsBoundedTo25MbSteps() {
        assertEquals(50,discoveryCacheLimitMb(null));assertEquals(0,discoveryCacheLimitMb(-25))
        assertEquals(25,discoveryCacheLimitMb(49));assertEquals(1000,discoveryCacheLimitMb(1100))
        for(value in 0..1000 step 25) assertEquals(value,discoveryCacheLimitMb(value))
    }
}
