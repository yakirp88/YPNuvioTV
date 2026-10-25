package com.nuvio.tv.ui.screens.player

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroDetectionFailureBoundaryTest {
    @Test
    fun `preflight read failure does not cancel playback sibling`() = runBlocking {
        coroutineScope {
            val playing = CompletableDeferred<Unit>()
            val playback = launch { playing.complete(Unit); awaitCancellation() }
            playing.await()
            var failures = 0
            runIntroDetectionSafely(onFailure = { failures++ }) {
                // Represents a credential-store read failing before the per-analysis try block.
                flow<Boolean> { throw IOException("unavailable preferences") }.collect {}
            }
            assertEquals(1, failures)
            assertTrue(playback.isActive)
            playback.cancelAndJoin()
        }
    }

    @Test
    fun `collector failure is contained as well as upstream failure`() = runBlocking {
        var failures = 0
        runIntroDetectionSafely(onFailure = { failures++ }) {
            flow { emit(true) }.collect { throw IllegalStateException("review unavailable") }
        }
        assertEquals(1, failures)
    }

    @Test
    fun `player cancellation propagates without being reported as detection failure`() = runBlocking {
        val cancellation = CancellationException("player closed")
        var failures = 0
        var received: CancellationException? = null
        try {
            runIntroDetectionSafely(onFailure = { failures++ }) { throw cancellation }
        } catch (expected: CancellationException) {
            received = expected
        }
        assertTrue(received === cancellation)
        assertEquals(0, failures)
    }
}
