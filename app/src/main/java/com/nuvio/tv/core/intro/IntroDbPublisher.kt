package com.nuvio.tv.core.intro

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import org.json.JSONObject

/** Dedicated client so source/API credentials never pass through app logging interceptors. */
internal object IntroDbPublisher {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun submit(key: String, imdb: String, season: Int?, episode: Int?, movie: Boolean, segment: AiSegmentProposal) {
        require(imdb.matches(Regex("tt[0-9]{7,10}")))
        require(movie || (season != null && season >= 0 && episode != null && episode > 0))
        // Longer Arabic intros need metadata validation before enabling the special API limit.
        require(segment.type != "intro" || segment.endMs - segment.startMs <= 180000)
        val body = JSONObject().put("imdb_id", imdb).put("segment_type", segment.type)
            .put("start_sec", segment.startMs / 1000.0).put("end_sec", segment.endMs / 1000.0)
        if (movie) body.put("is_movie", true) else body.put("season", season).put("episode", episode)
        val request = Request.Builder().url("https://api.introdb.app/submit")
            .header("X-API-Key", key).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        suspendCancellableCoroutine<Unit> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(IOException("Submission outcome unknown"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val success = response.use { it.isSuccessful }
                    if (!continuation.isCancelled) {
                        if (success) continuation.resume(Unit)
                        else continuation.resumeWithException(IOException("Submission rejected"))
                    }
                }
            })
        }
    }
}
