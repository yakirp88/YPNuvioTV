package com.nuvio.tv.core.intro

import android.media.MediaDataSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Bounded random reads, not a movie download. Never forwards credentials across redirects. */
internal class IntroRangeReader(private val url: String, private val headers: Map<String, String>,
    private val active: () -> Boolean, private val maxRequests: Int = 128) : java.io.Closeable {
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val deadline = System.nanoTime() + 30_000_000_000L
    private var size = -1L
    var downloaded = 0L
        private set
    var requests = 0
        private set
    private var closed = false
    private val cache = linkedMapOf<Long, ByteArray>()
    private fun block(position: Long): ByteArray {
        if (closed || !active() || Thread.currentThread().isInterrupted || System.nanoTime() > deadline) throw IOException("Evidence budget exhausted")
        val start = position / 262_144 * 262_144
        cache[start]?.let { return it }
        if (requests >= maxRequests || downloaded >= 32L * 1024 * 1024) throw IOException("Evidence range budget exhausted")
        val request = Request.Builder().url(url).apply {
            headers.filterKeys { it.lowercase() !in setOf("range", "host", "connection", "accept-encoding") }
                .forEach { (key, value) -> header(key, value) }
        }.header("Accept-Encoding", "identity").header("Range", "bytes=$start-${start + 262_143}").build()
        requests++
        val result = client.newCall(request).execute().use { response ->
            val range = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(response.header("Content-Range").orEmpty())
            if (response.code != 206 || range == null || range.groupValues[1].toLong() != start) throw IOException("Seekable byte ranges unavailable")
            size = range.groupValues[3].toLong()
            val source = response.body?.source() ?: throw IOException("No evidence body")
            if (source.request(262_145)) throw IOException("Evidence range too large")
            source.readByteArray().also {
                if (it.size.toLong() != range.groupValues[2].toLong() - start + 1) throw IOException("Evidence range mismatch")
            }
        }
        downloaded += result.size
        cache[start] = result
        while (cache.size > 8) cache.remove(cache.keys.first())
        return result
    }
    @Synchronized fun getSize(): Long { if (size < 0) block(0); return size }
    @Synchronized fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position < 0 || offset < 0 || length < 0 || length > buffer.size - offset) throw IOException("Invalid evidence read")
        if (length == 0) return 0
        if (size >= 0 && position >= size) return -1
        val data = block(position)
        val index = (position % 262_144).toInt()
        val count = minOf(length, data.size - index)
        if (count <= 0) return -1
        data.copyInto(buffer, offset, index, index + count)
        return count
    }
    @Synchronized override fun close() { closed = true; cache.clear(); client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
}

internal class IntroRangeSource(url: String, headers: Map<String, String>, active: () -> Boolean) : MediaDataSource() {
    private val reader = IntroRangeReader(url, headers, active)
    override fun getSize() = reader.getSize()
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int) = reader.readAt(position, buffer, offset, size)
    override fun close() = reader.close()
}
