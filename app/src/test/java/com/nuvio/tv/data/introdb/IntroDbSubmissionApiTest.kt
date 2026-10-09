package com.nuvio.tv.data.introdb

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response as HttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class IntroDbSubmissionApiTest {
    @Test fun submissionBuildsAndSendsJsonWithUserKey() = runBlocking {
        var sentBody = ""
        var sentKey: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("POST", request.method)
            assertEquals("/submit", request.url.encodedPath)
            sentKey = request.header("X-API-Key")
            sentBody = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            HttpResponse.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("{}".toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.introdb.app/")
            .client(client).addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .validateEagerly(true).build().create(IntroDbSubmissionApi::class.java)
        val response = api.submit("idb_test_only", mapOf(
            "imdb_id" to "tt0203259", "segment_type" to "intro",
            "season" to 23, "episode" to 1, "start_sec" to 259.513, "end_sec" to 305.300))
        response.body()?.close()
        assertEquals(200, response.code())
        assertEquals("idb_test_only", sentKey)
        val json = Moshi.Builder().build().adapter(Map::class.java).fromJson(sentBody)!!
        assertEquals("tt0203259", json["imdb_id"])
        assertEquals("intro", json["segment_type"])
        assertEquals(23.0, json["season"])
        assertEquals(1.0, json["episode"])
        assertEquals(259.513, json["start_sec"])
        assertEquals(305.3, json["end_sec"])
    }
}
