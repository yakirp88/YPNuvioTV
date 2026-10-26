package com.nuvio.tv.core.intro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal data class EmbeddedTextReadPoint(val timeMs: Long, val clusterPosition: Long,
    val relativePosition: Long?, val durationMs: Long?)
internal data class EmbeddedTextReadSource(val track: Int, val codec: String, val label: String,
    val segmentStart: Long, val scaleNs: Long, val points: List<EmbeddedTextReadPoint>)

/** Indexed text blocks only: skip video/audio payload by EBML element size, never decode them. */
internal object IndexedIntroSubtitles {
    private data class Element(val id: Long, val data: Long, val end: Long)
    private fun vint(bytes: ByteArray, at: Int, keepMarker: Boolean): Pair<Long, Int> {
        require(at in bytes.indices)
        val first = bytes[at].toInt() and 255
        var width = 1; var mask = 128
        while (width <= 8 && (first and mask) == 0) { width++; mask = mask shr 1 }
        require(width <= 8 && at + width <= bytes.size)
        var value = (if (keepMarker) first else first and (mask - 1)).toLong()
        for (i in 1 until width) value = (value shl 8) or (bytes[at + i].toLong() and 255)
        require(keepMarker || value != (1L shl (7 * width)) - 1)
        return value to width
    }
    private fun element(position: Long, read: (Long, Int) -> ByteArray): Element {
        val header = read(position, 12)
        val (id, idWidth) = vint(header, 0, true)
        require(idWidth <= 4)
        val (size, sizeWidth) = vint(header, idWidth, false)
        val data = Math.addExact(position, (idWidth + sizeWidth).toLong())
        return Element(id, data, Math.addExact(data, size))
    }
    private fun uint(element: Element, read: (Long, Int) -> ByteArray): Long {
        val length = element.end - element.data
        require(length in 1..8)
        return read(element.data, length.toInt()).fold(0L) { a, b -> (a shl 8) or (b.toLong() and 255) }.also { require(it >= 0) }
    }

    /** Pure packet path also used by regression fixtures; absolute offsets remain private in memory. */
    fun decodePoint(source: EmbeddedTextReadSource, point: EmbeddedTextReadPoint,
        read: (Long, Int) -> ByteArray): SubtitleEvidenceAnalyzer.Cue? = try {
        val cluster = element(Math.addExact(source.segmentStart, point.clusterPosition), read)
        require(cluster.id == 0x1F43B675L)
        var at = cluster.data
        var timestamp: Long? = null
        repeat(8) {
            if (timestamp == null && at < cluster.end) {
                val child = element(at, read); require(child.end <= cluster.end && child.end > at)
                if (child.id == 0xE7L) timestamp = uint(child, read)
                at = child.end
            }
        }
        val clusterTime = timestamp ?: error("Missing cluster timestamp")
        var next = point.relativePosition?.let { Math.addExact(cluster.data, it) } ?: cluster.data
        var answer: SubtitleEvidenceAnalyzer.Cue? = null
        var steps = 0
        while (next < cluster.end && steps++ < 128 && answer == null) {
            val container = element(next, read); require(container.end <= cluster.end && container.end > next)
            var block: Element? = null
            var duration = point.durationMs
            if (container.id == 0xA3L) block = container
            if (container.id == 0xA0L) {
                var childAt = container.data; var children = 0
                while (childAt < container.end && children++ < 12) {
                    val child = element(childAt, read); require(child.end <= container.end && child.end > childAt)
                    if (child.id == 0xA1L) block = child
                    if (child.id == 0x9BL) duration = Math.multiplyExact(uint(child, read), source.scaleNs) / 1_000_000
                    childAt = child.end
                }
            }
            block?.let {
                val n = it.end - it.data
                if (n in 5..65_536) {
                    val bytes = read(it.data, n.toInt())
                    val (track, width) = vint(bytes, 0, false)
                    val relative = (((bytes[width].toInt() and 255) shl 8) or (bytes[width + 1].toInt() and 255)).toShort().toLong()
                    val time = Math.multiplyExact(Math.addExact(clusterTime, relative), source.scaleNs) / 1_000_000
                    if (track == source.track.toLong() && (bytes[width + 2].toInt() and 6) == 0 && abs(time - point.timeMs) <= 2) {
                        val mime = if (source.codec.contains("ASS") || source.codec.contains("SSA")) "text/x-ass" else "application/x-subrip"
                        IntroSubtitlePolicy.decode(bytes.copyOfRange(width + 3, bytes.size), mime)?.let { text ->
                            val end = Math.addExact(time, duration?.takeIf { value -> value in 1..120_000 } ?: 1)
                            answer = SubtitleEvidenceAnalyzer.Cue(time, end, text)
                        }
                    }
                }
            }
            if (point.relativePosition != null) break
            next = container.end
        }
        answer
    } catch (_: Exception) { null }

    suspend fun read(url: String, headers: Map<String, String>, duration: Long,
        sources: List<EmbeddedTextReadSource>): List<IntroCaptionCandidate> = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job]
        val reader = IntroRangeReader(url, headers, { job?.isActive != false }, maxRequests = 160, blockSize = 8192)
        fun bytes(at: Long, n: Int): ByteArray {
            val result = ByteArray(n); var done = 0
            while (done < n) { val count = reader.readAt(at + done, result, done, n - done); check(count > 0); done += count }
            return result
        }
        try {
            val output = mutableListOf<IntroCaptionCandidate>()
            for (source in sources.sortedByDescending { IntroSubtitlePolicy.descriptive(it.label) }.take(2)) {
                val opening = source.points.filter { it.timeMs < minOf(600_000L, duration / 4) }
                val ending = source.points.filter { it.timeMs >= duration - minOf(900_000L, duration / 4) }
                fun sample(items: List<EmbeddedTextReadPoint>): List<EmbeddedTextReadPoint> = if (items.size <= 32) items else
                    (0 until 32).map { items[it * (items.size - 1) / 31] }
                val a = sample(opening); val b = sample(ending)
                val cues = mutableListOf<SubtitleEvidenceAnalyzer.Cue>()
                for (i in 0 until maxOf(a.size, b.size)) {
                    for (point in listOfNotNull(a.getOrNull(i), b.getOrNull(i))) {
                        currentCoroutineContext().ensureActive()
                        decodePoint(source, point, ::bytes)?.let { cues.add(it) }
                    }
                }
                if (cues.isNotEmpty()) output += IntroCaptionCandidate(cues.distinctBy { it.startMs }.sortedBy { it.startMs }, source.label, true)
            }
            output
        } finally { reader.close() }
    }
}
