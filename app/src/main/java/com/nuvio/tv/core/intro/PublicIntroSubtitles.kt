package com.nuvio.tv.core.intro

import com.google.gson.JsonParser
import com.nuvio.tv.domain.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/** Official public Stremio OpenSubtitles provider, independent of installed add-ons. */
internal object PublicIntroSubtitles {
    private val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).build()
    private fun get(url: String, limit: Long): String = client.newCall(Request.Builder().url(url).build()).execute().use {
        check(it.isSuccessful)
        val source = checkNotNull(it.body).source()
        check(!source.request(limit + 1))
        source.readUtf8()
    }
    suspend fun discover(type: String, videoId: String): List<Subtitle> = withContext(Dispatchers.IO) {
        require(type in setOf("series", "movie"))
        require(videoId.matches(Regex("tt[0-9]{7,10}(:[0-9]{1,4}:[0-9]{1,5})?")))
        val url = "https://opensubtitles-v3.strem.io/".toHttpUrl().newBuilder()
            .addPathSegment("subtitles").addPathSegment(type).addPathSegment("$videoId.json").build()
        val array = JsonParser.parseString(get(url.toString(), 1_048_576)).asJsonObject.getAsJsonArray("subtitles")
        array?.take(400)?.mapNotNull { value ->
            runCatching {
                val item = value.asJsonObject
                val source = item.get("url").asString
                if (!source.startsWith("https://")) return@runCatching null
                Subtitle(item.get("id")?.asString.orEmpty().take(256), source, item.get("lang")?.asString.orEmpty().take(20),
                    "OpenSubtitles v3", null)
            }.getOrNull()
        }.orEmpty()
    }
    suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://"))
        get(url, 2_000_000)
    }
}
