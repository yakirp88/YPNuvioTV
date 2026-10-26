package com.nuvio.tv.core.intro

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class GeminiHttpTransportTest {
    @Test fun delayedCloudHeadersOutliveOldTenSecondDefault() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeadersDelay(11, TimeUnit.SECONDS).setBody("ok"))
            val client = GeminiHttpTransport.client()
            assertEquals(60_000, client.writeTimeoutMillis)
            assertEquals(120_000, client.readTimeoutMillis)
            assertEquals(180_000, client.callTimeoutMillis)
            assertFalse(client.retryOnConnectionFailure)
            assertFalse(client.followRedirects)
            assertEquals("ok", GeminiHttpTransport.execute(client, Request.Builder().url(server.url("/")).build()))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun totalDeadlineIsReportedAsTimeout() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = GeminiHttpTransport.client().newBuilder().callTimeout(200, TimeUnit.MILLISECONDS).build()
            try {
                GeminiHttpTransport.execute(client, Request.Builder().url(server.url("/")).build())
                fail("Expected deadline failure")
            } catch (error: GeminiIntroAnalyzer.Failure) {
                assertEquals(GeminiIntroAnalyzer.FailureKind.TIMEOUT, error.kind)
            }
        }
    }

    @Test fun bodyReadTimeoutKeepsNetworkClassification() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("late").setBodyDelay(1, TimeUnit.SECONDS))
            val client = GeminiHttpTransport.client().newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build()
            try {
                GeminiHttpTransport.execute(client, Request.Builder().url(server.url("/")).build())
                fail("Expected read timeout")
            } catch (error: GeminiIntroAnalyzer.Failure) {
                assertEquals(GeminiIntroAnalyzer.FailureKind.TIMEOUT, error.kind)
            }
        }
    }

    @Test fun callerCancellationStaysCancellation() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try {
                withTimeout(200) {
                    GeminiHttpTransport.execute(GeminiHttpTransport.client(), Request.Builder().url(server.url("/")).build())
                }
                fail("Expected cancellation")
            } catch (_: TimeoutCancellationException) { /* Do not wrap cancellation as a provider failure. */ }
        }
    }

    @Test fun transientServerFailuresRetryWithBoundedBackoff() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(502).setBody("{}"))
            server.enqueue(MockResponse().setBody("ok"))
            val waits = mutableListOf<Long>()
            val attempts = mutableListOf<Int>()
            val result = GeminiHttpTransport.executeWithRetry(GeminiHttpTransport.client(),
                Request.Builder().url(server.url("/")).build(), pause = { waits.add(it); Unit },
                onRetry = { attempt, _ -> attempts.add(attempt); Unit })
            assertEquals("ok", result)
            assertEquals(listOf(2, 3), attempts)
            assertTrue(waits[0] in 1000L..1500L && waits[1] in 2000L..2500L)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun retriesStopAtThreeAndNeverRetryPermissionFailures() = runBlocking {
        for (status in listOf(503, 403)) {
            MockWebServer().use { server ->
                repeat(3) { server.enqueue(MockResponse().setResponseCode(status).setBody("{}")) }
                try {
                    GeminiHttpTransport.executeWithRetry(GeminiHttpTransport.client(),
                        Request.Builder().url(server.url("/")).build(), pause = {}, onRetry = { _, _ -> })
                    fail("Expected HTTP failure")
                } catch (error: GeminiIntroAnalyzer.HttpFailure) { assertEquals(status, error.status) }
                assertEquals(if (status == 503) 3 else 1, server.requestCount)
            }
        }
    }

    @Test fun cancellationDuringBackoffPreventsAnotherRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            try {
                GeminiHttpTransport.executeWithRetry(GeminiHttpTransport.client(),
                    Request.Builder().url(server.url("/")).build(),
                    pause = { throw kotlinx.coroutines.CancellationException("cancel") }, onRetry = { _, _ -> })
                fail("Expected cancellation")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun providerDetailIsAllowlistedAndNeverCopiesTheMessage() {
        val text = """{"error":{"status":"PERMISSION_DENIED","message":"private-key https://secret","details":[{"reason":"API_KEY_INVALID","metadata":{"key":"secret"}}]}}"""
        assertEquals("provider_key_invalid", GeminiProviderError.reason(text))
        assertEquals("provider_unknown", GeminiProviderError.reason("""{"error":{"status":"https://secret","message":"secret","details":[{"reason":"private-key"}]}}"""))
        assertEquals("provider_unknown", GeminiProviderError.reason("bad"))
        assertEquals("provider_unknown", GeminiProviderError.reason("x".repeat(16385)))
    }
}
