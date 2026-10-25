package com.nuvio.tv.core.intro

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class VideoEvidenceFrame(val requestedTimeMs: Long, val jpeg: ByteArray)

/** Separate decoder: never seeks the active player. Some remote/container formats are unsupported. */
internal object VideoEvidenceSampler {
    private val decoderSlot = Semaphore(1)

    suspend fun sample(url: String, headers: Map<String, String>, durationMs: Long, openingNeeded: Boolean, endingNeeded: Boolean, times: LongArray? = null): List<VideoEvidenceFrame> =
        decoderSlot.withPermit {
            withContext(Dispatchers.IO) {
                require(durationMs > 0)
                require(url.startsWith("https://") || url.startsWith("http://"))
                require(times == null || (times.size <= 24 && times.all { it in 0 until durationMs }))
                val retriever = MediaMetadataRetriever()
                val frames = mutableListOf<VideoEvidenceFrame>()
                val started = SystemClock.elapsedRealtime()
                try {
                    currentCoroutineContext().ensureActive()
                    retriever.setDataSource(url, headers)
                    for (time in times ?: VideoSamplingPlan.times(durationMs, openingNeeded, endingNeeded)) {
                        currentCoroutineContext().ensureActive()
                        // Native decoder calls cannot be interrupted; budget is checked between calls.
                        if (SystemClock.elapsedRealtime() - started > 60_000) break
                        val bitmap = if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 480, 270)
                        } else retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (bitmap == null) continue
                        try {
                            val scale = minOf(1f, 480f / bitmap.width, 270f / bitmap.height)
                            val small = if (scale < 1) Bitmap.createScaledBitmap(bitmap,
                                (bitmap.width * scale).toInt().coerceAtLeast(1),
                                (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                            try {
                                val output = ByteArrayOutputStream()
                                if (small.compress(Bitmap.CompressFormat.JPEG, 65, output) && output.size() <= 150_000) {
                                    frames.add(VideoEvidenceFrame(time, output.toByteArray()))
                                }
                            } finally { if (small !== bitmap) small.recycle() }
                        } finally { bitmap.recycle() }
                    }
                } finally { retriever.release() }
                frames
            }
        }
}
