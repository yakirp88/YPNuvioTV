package com.nuvio.tv.core.diagnostics

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Explicit user action only. No credentials, redirects or automatic retries. */
internal object DetectionLogUpload {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(35, TimeUnit.SECONDS).build()

    fun validLink(value: String): Boolean = value.matches(Regex("https://dpaste\\.org/[A-Za-z0-9]{3,32}/?"))

    suspend fun upload(): String {
        val snapshot = YpCrashDiagnostics.latestDetectionLog()
        check(snapshot.length <= 25_000)
        val request = Request.Builder().url("https://dpaste.org/api/").post(FormBody.Builder()
            .add("content", snapshot).add("format", "url").add("expires", "604800").build()).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(IOException("Log upload unavailable"))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val link = response.use {
                            check(it.isSuccessful)
                            val source = requireNotNull(it.body).source()
                            check(!source.request(2049))
                            source.readUtf8().trim()
                        }
                        check(validLink(link))
                        if (!continuation.isCancelled) continuation.resume(link)
                    } catch (_: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(IOException("Log upload rejected"))
                    }
                }
            })
        }
    }
}
