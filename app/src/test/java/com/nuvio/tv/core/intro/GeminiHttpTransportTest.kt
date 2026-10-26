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
}
