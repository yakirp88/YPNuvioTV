package com.nuvio.tv.data.introdb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.tv.data.remote.api.IntroDbApi
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface IntroDbSubmissionApi {
    @POST("submit")
    suspend fun submit(@Header("X-API-Key") key: String, @Body body: Map<String, @JvmSuppressWildcards Any>): Response<ResponseBody>
}

/** Dedicated transport: no media headers, HTTP logging, or cross-host redirects. */
@Singleton
class IntroDbReportRepository @Inject constructor(@ApplicationContext context: Context, moshi: Moshi) {
    private val preferences = context.getSharedPreferences("yp_introdb_private", Context.MODE_PRIVATE)
    private val retrofit = Retrofit.Builder().baseUrl("https://api.introdb.app/")
        .client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).build())
        .addConverterFactory(MoshiConverterFactory.create(moshi)).build()
    private val reader = retrofit.create(IntroDbApi::class.java)
    private val writer = retrofit.create(IntroDbSubmissionApi::class.java)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", true)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("yp_introdb_api_key", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("yp_introdb_api_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun apiKey(): String? = runCatching {
        val encrypted = preferences.getString("key", null) ?: return null
        val parts = encrypted.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun saveApiKey(value: String) {
        val key = value.trim()
        if (key.isEmpty()) { preferences.edit().remove("key").apply(); return }
        require(key.startsWith("idb_") && !key.any { it.isWhitespace() })
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(preferences.edit().putString("key", encrypted).commit())
    }

    suspend fun existing(media: ReportMedia): Set<ReportSegment> {
        require(media.valid)
        val response = reader.getSegments(media.imdbId, media.season, media.episode, media.movie.takeIf { it })
        // A failed read is never interpreted as permission to submit.
        if (response.code() == 404 || response.code() == 204) return emptySet()
        check(response.isSuccessful) { "HTTP_${response.code()}" }
        val body = response.body() ?: error("EMPTY_RESPONSE")
        return buildSet {
            if (body.intro != null) add(ReportSegment.INTRO)
            if (body.recap != null) add(ReportSegment.RECAP)
            if (body.outro != null) add(ReportSegment.OUTRO)
        }
    }

    /** Returns false if another contributor filled this segment in the meantime. */
    suspend fun submit(media: ReportMedia, segment: ReportSegment, start: Long, end: Long, duration: Long): Boolean {
        require(media.valid && IntroDbReportRules.validRange(start, end, duration))
        check(segment in IntroDbReportRules.available(media, emptySet()))
        val key = withContext(Dispatchers.IO) { apiKey() } ?: error("NO_KEY")
        if (segment in existing(media)) return false
        val body = mutableMapOf<String, Any>("imdb_id" to media.imdbId, "segment_type" to segment.apiName,
            "start_sec" to start / 1000.0, "end_sec" to end / 1000.0)
        if (media.movie) body["is_movie"] = true else {
            body["season"] = requireNotNull(media.season); body["episode"] = requireNotNull(media.episode)
        }
        val response = writer.submit(key, body)
        response.body()?.close()
        response.errorBody()?.close()
        if (response.code() == 409) return false
        check(response.isSuccessful) { "HTTP_${response.code()}" }
        return true
    }

    data class Draft(val segment: ReportSegment, val start: Long, val end: Long, val duration: Long)

    fun draft(media: ReportMedia): Draft? = runCatching {
        val parts = preferences.getString("draft:${media.key}", null)?.split(":") ?: return null
        Draft(ReportSegment.valueOf(parts[0]), parts[1].toLong(), parts[2].toLong(), parts[3].toLong())
            .takeIf { IntroDbReportRules.validRange(it.start, it.end, it.duration) }
    }.getOrNull()

    fun saveDraft(media: ReportMedia, segment: ReportSegment, start: Long, end: Long, duration: Long) {
        preferences.edit().putString("draft:${media.key}", "${segment.name}:$start:$end:$duration").apply()
    }

    fun removeDraft(media: ReportMedia) { preferences.edit().remove("draft:${media.key}").apply() }

    fun submitted(media: ReportMedia): Set<ReportSegment> = preferences.getStringSet("submitted:${media.key}", emptySet())
        .orEmpty().mapNotNull { name -> ReportSegment.entries.find { it.apiName == name } }.toSet()

    fun markSubmitted(media: ReportMedia, segment: ReportSegment) {
        preferences.edit().putStringSet("submitted:${media.key}", (submitted(media) + segment).map { it.apiName }.toSet()).apply()
    }
}
