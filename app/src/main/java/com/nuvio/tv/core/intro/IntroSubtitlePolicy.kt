package com.nuvio.tv.core.intro

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.math.abs

/** Ranking and timing checks do not assert that an SDH clue proves a skip boundary. */
internal object IntroSubtitlePolicy {
    fun descriptive(label: String) = Regex("(?i)(\\bSDH\\b|\\bHI\\b|\\bCC\\b|hearing|impaired|כבדי|שמיעה)").containsMatchIn(label)
    fun descriptionCount(cues: List<SubtitleEvidenceAnalyzer.Cue>): Int = cues.count {
        (it.text.contains('[') && it.text.contains(']')) || it.text.contains('♪') ||
            (it.text.contains('(') && it.text.contains(')'))
    }
    fun valid(cues: List<SubtitleEvidenceAnalyzer.Cue>, duration: Long) = cues.size >= 8 &&
        cues.all { it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= duration + 2000 && it.text.length <= 4000 } &&
        cues.zipWithNext().all { (a, b) -> a.startMs <= b.startMs }

    /** Strong constant-offset consensus across both halves; no extrapolation from duration alone. */
    fun alignOffset(cues: List<SubtitleEvidenceAnalyzer.Cue>, references: List<Long>, duration: Long): Long? {
        if (cues.size < 30 || references.size < 30 || cues.last().startMs - cues.first().startMs < duration * 6 / 10) return null
        val ref = references.distinct().sorted()
        val sample = (0 until 64).map { cues[it * (cues.size - 1) / 63].startMs }.distinct()
        fun nearest(v: Long): Long {
            val i = ref.binarySearch(v).let { if (it >= 0) it else -it - 1 }
            return listOfNotNull(ref.getOrNull(i), ref.getOrNull(i - 1)).minBy { abs(it - v) }
        }
        val offsets = mutableMapOf<Long, Int>()
        for (at in sample) {
            val from = ref.binarySearch(at - 120_000).let { if (it >= 0) it else -it - 1 }
            var i = from
            while (i < ref.size && ref[i] <= at + 120_000) {
                val offset = Math.round((ref[i] - at) / 100.0) * 100
                offsets[offset] = (offsets[offset] ?: 0) + 1
                i++
            }
        }
        val scored = offsets.entries.sortedByDescending { it.value }.take(24).map { (offset, _) ->
            val errors = sample.map { abs(nearest(it + offset) - (it + offset)) }
            Triple(offset, errors.count { it <= 500 }.toDouble() / sample.size, errors.sorted()[errors.size / 2])
        }.sortedWith(compareByDescending<Triple<Long, Double, Long>> { it.second }.thenBy { it.third })
        val best = scored.firstOrNull() ?: return null
        if (best.second < .88 || best.third > 180) return null
        val distant = scored.firstOrNull { abs(it.first - best.first) >= 1500 }
        if (distant != null && best.second - distant.second < .12) return null
        return best.first
    }

    fun decode(bytes: ByteArray, mime: String): String? { return try {
        if (bytes.isEmpty() || bytes.size > 65_536) null else {
            val payload = if (mime == "text/3gpp" || mime == "application/x-quicktime-tx3g") {
                if (bytes.size < 2) return null
                val n = ((bytes[0].toInt() and 255) shl 8) or (bytes[1].toInt() and 255)
                if (n > bytes.size - 2) return null
                bytes.copyOfRange(2, 2 + n)
            } else bytes
            var text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString().trim()
            if (mime.contains("ssa") || mime.contains("ass")) {
                text = if (text.startsWith("Dialogue:")) text.substringAfter(':').split(',', limit = 10).last()
                    else text.split(',', limit = 9).let { if (it.size == 9) it.last() else text }
                text = text.replace("\\N", "\n").replace("\\n", "\n")
            }
            text.takeIf { it.isNotBlank() && it.length <= 4000 && '\u0000' !in it }
        }
    } catch (_: Exception) { null }
    }
}
