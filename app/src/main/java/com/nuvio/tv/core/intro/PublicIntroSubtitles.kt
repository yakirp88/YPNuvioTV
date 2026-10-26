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
internal data class IntroSubtitleOption(val subtitle: Subtitle, val label: String = subtitle.id)

internal object PublicIntroSubtitles {
    class HttpFailure(val status: Int) : java.io.IOException("Subtitle provider HTTP failure")
    private val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).build()
    private fun get(url: String, limit: Long): String = client.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) throw HttpFailure(it.code)
        val source = checkNotNull(it.body).source()
        check(!source.request(limit + 1))
        source.readUtf8()
    }
    suspend fun discover(type: String, videoId: String): List<IntroSubtitleOption> = withContext(Dispatchers.IO) {
        require(type in setOf("series", "movie"))
        require(videoId.matches(Regex("tt[0-9]{7,10}(:[0-9]{1,4}:[0-9]{1,5})?")))
        val url = "https://opensubtitles-v3.strem.io/".toHttpUrl().newBuilder()
            .addPathSegment("subtitles").addPathSegment(type).addPathSegment("$videoId.json").build()
        parse(get(url.toString(), 1_048_576))
    }
    /** Keep analysis metadata without changing the viewing subtitle model or filtering languages. */
    internal fun parse(body: String): List<IntroSubtitleOption> {
        val array = JsonParser.parseString(body).asJsonObject.getAsJsonArray("subtitles")
            ?: error("Invalid subtitle provider response")
        return array.take(1000).mapNotNull { value ->
            runCatching {
                val item = value.asJsonObject
                val source = item.get("url").asString
                if (!source.startsWith("https://")) return@runCatching null
                fun field(name: String) = item.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().take(256)
                val id = field("id")
                val hi = listOf("hearing_impaired", "hearingImpaired", "isHearingImpaired", "sdh", "is_sdh")
                    .any { field(it).lowercase() in setOf("true", "1", "yes") }
                val label = (listOf(id, field("label"), field("name"), field("release"), field("filename"),
                    field("lang"), if (hi) "SDH" else "")).joinToString(" ").take(1600)
                IntroSubtitleOption(Subtitle(id, source, field("lang").take(20), "OpenSubtitles v3", null), label)
            }.getOrNull()
        }
    }
    suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        require(url.startsWith("https://"))
        get(url, 2_000_000)
    }
}
