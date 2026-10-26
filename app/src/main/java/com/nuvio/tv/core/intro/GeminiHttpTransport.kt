package com.nuvio.tv.core.intro

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import com.nuvio.tv.core.diagnostics.YpCrashDiagnostics
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Explicit per-operation budgets: OkHttp's default 10 seconds is too short for visual analysis. */
internal object GeminiHttpTransport {
    fun client(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun executeWithRetry(client: OkHttpClient, request: Request,
        pause: suspend (Long) -> Unit = { delay(it) },
        onRetry: (Int, Int) -> Unit = { attempt, status ->
            YpCrashDiagnostics.logDetectionEvent("cloud_retry", "retry_wait", mapOf("attempt" to attempt.toLong(), "http_status" to status.toLong()))
        }
    ): String {
        try {
            return withTimeout(240_000) {
                var attempt = 1
                while (true) {
                    currentCoroutineContext().ensureActive()
                    try { return@withTimeout execute(client, request) }
                    catch (error: GeminiIntroAnalyzer.HttpFailure) {
                        if (error.status !in setOf(502, 503, 504) || attempt >= 3) throw error
                        attempt++
                        onRetry(attempt, error.status)
                        pause((1L shl (attempt - 2)) * 1000 + kotlin.random.Random.nextLong(0, 501))
                    }
                }
                @Suppress("UNREACHABLE_CODE") error("Unreachable")
            }
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw GeminiIntroAnalyzer.Failure(GeminiIntroAnalyzer.FailureKind.TIMEOUT)
        }
    }

    private fun failure(error: IOException) = GeminiIntroAnalyzer.Failure(when (error) {
        is SocketTimeoutException, is InterruptedIOException -> GeminiIntroAnalyzer.FailureKind.TIMEOUT
        is UnknownHostException -> GeminiIntroAnalyzer.FailureKind.DNS
        is SSLException -> GeminiIntroAnalyzer.FailureKind.TLS
        else -> GeminiIntroAnalyzer.FailureKind.NETWORK
    })

    suspend fun execute(client: OkHttpClient, request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) {
                            val source = it.body?.source()
                            val reason = try {
                                if (source == null || source.request(16_385)) "provider_unknown"
                                else GeminiProviderError.reason(source.readUtf8())
                            } catch (_: Exception) { "provider_unknown" }
                            throw GeminiIntroAnalyzer.HttpFailure(it.code, reason)
                        }
                        val source = requireNotNull(it.body).source()
                        require(!source.request(262_145)) { "Model response too large" }
                        source.readUtf8()
                    }
                    if (!continuation.isCancelled) continuation.resume(text)
                } catch (error: Exception) {
                    val safe = when (error) {
                        is GeminiIntroAnalyzer.HttpFailure -> error
                        is IOException -> failure(error)
                        else -> GeminiIntroAnalyzer.Failure(GeminiIntroAnalyzer.FailureKind.RESPONSE)
                    }
                    if (!continuation.isCancelled) continuation.resumeWithException(safe)
                }
            }
        })
    }
}
