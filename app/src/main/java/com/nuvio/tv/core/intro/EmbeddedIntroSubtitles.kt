package com.nuvio.tv.core.intro

import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class IntroCaptionCandidate(val cues: List<SubtitleEvidenceAnalyzer.Cue>, val label: String,
    val embedded: Boolean, val selected: Boolean = false, val offsetMs: Long = 0L)

/** Best effort text tracks only. Bitmap subtitles need OCR and are never passed off as text. */
internal object EmbeddedIntroSubtitles {
    private val supported = setOf("application/x-subrip", "text/vtt", "text/ssa", "text/x-ssa", "text/x-ass", "text/3gpp", "application/x-quicktime-tx3g")
    suspend fun read(url: String, headers: Map<String, String>, duration: Long): List<IntroCaptionCandidate> = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job]
        val source = IntroRangeSource(url, headers) { job?.isActive != false }
        val extractor = MediaExtractor()
        val collected = linkedMapOf<Int, MutableList<SubtitleEvidenceAnalyzer.Cue>>()
        val formats = mutableMapOf<Int, Pair<String, String>>()
        try {
            extractor.setDataSource(source)
            val tracks = (0 until extractor.trackCount).mapNotNull { index ->
                val f = extractor.getTrackFormat(index)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime !in supported) null else {
                    val label = listOf("language", "title").joinToString(" ") { key -> runCatching { f.getString(key) }.getOrNull().orEmpty() }
                    Triple(index, mime, label)
                }
            }.sortedByDescending { IntroSubtitlePolicy.descriptive(it.third) }.take(4)
            tracks.forEach { (index, mime, label) -> extractor.selectTrack(index); collected[index] = mutableListOf(); formats[index] = mime to label }
            com.nuvio.tv.core.diagnostics.YpCrashDiagnostics.logDetectionEvent("embedded_scan", if (tracks.isEmpty()) "subtitle_unavailable" else "waiting",
                mapOf("embedded_count" to tracks.size.toLong()))
            val buffer = ByteBuffer.allocate(65_536)
            val windows = listOf(0L to minOf(600_000L, duration / 4), (duration - minOf(900_000L, duration / 4)) to duration)
            for ((start, end) in windows) {
                if (tracks.isEmpty()) break
                currentCoroutineContext().ensureActive()
                extractor.seekTo(start * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                var steps = 0
                while (steps++ < 5000) {
                    currentCoroutineContext().ensureActive()
                    val index = extractor.sampleTrackIndex
                    val at = extractor.sampleTime / 1000
                    if (index < 0 || at > end) break
                    if (at >= start && at < duration && collected[index] != null && collected.getValue(index).size < 1200) {
                        buffer.clear()
                        val count = extractor.readSampleData(buffer, 0)
                        if (count in 1..65_536) {
                            val bytes = ByteArray(count); buffer.position(0); buffer.get(bytes)
                            IntroSubtitlePolicy.decode(bytes, formats.getValue(index).first)?.let { text ->
                                // Native extraction has start timestamps but no reliable end timestamp.
                                // A one-ms interval explicitly represents an observation, not a boundary.
                                collected.getValue(index).add(SubtitleEvidenceAnalyzer.Cue(at, at + 1, text))
                            }
                        }
                    }
                    if (!extractor.advance()) break
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep any successfully read evidence; source budgets remain enforced. */ }
        finally { runCatching { extractor.release() }; source.close() }
        collected.mapNotNull { (index, cues) ->
            val sorted = cues.distinctBy { it.startMs to it.text }.sortedBy { it.startMs }
            if (sorted.size < 3) null else IntroCaptionCandidate(sorted, formats[index]?.second.orEmpty(), true)
        }
    }
}
