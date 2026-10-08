@file:Suppress("UnsafeOptInUsageError")
package com.nuvio.tv.ui.screens.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

/** Copies only the video surface, so dialogs/controls never appear in the thumbnail. */
internal suspend fun PlayerRuntimeController.captureReportFrame(atMs: Long? = null): Bitmap? = try {
    captureReportFrameInternal(atMs)
} catch (cancel: kotlinx.coroutines.CancellationException) {
    throw cancel
} catch (_: Exception) {
    null
}

private suspend fun PlayerRuntimeController.captureReportFrameInternal(atMs: Long?): Bitmap? {
    if (atMs != null) {
        setPlaybackPaused(true)
        if (isUsingMpvEngine()) {
            val view = mpvView ?: return null
            view.seekReportFrame(atMs)
            val settled = withTimeoutOrNull(5000L) {
                while (!view.isReportSeekSettled(atMs)) delay(30)
                // Allow the surface compositor to consume the settled frame.
                delay(80)
                true
            } ?: false
            if (!settled) return null
        } else {
            val player = _exoPlayer ?: return null
            val rendered = CompletableDeferred<Unit>()
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (abs(player.currentPosition - atMs) <= 250L) rendered.complete(Unit)
                }
            }
            player.addListener(listener)
            val previousSeekParameters = player.seekParameters
            try {
                // Exact seek bypasses the app's normal keyframe/scrubbing performance path.
                player.setSeekParameters(SeekParameters.EXACT)
                player.seekTo(atMs.coerceAtMost((player.duration - 1).coerceAtLeast(0)))
                if (withTimeoutOrNull(5000L) { rendered.await(); true } != true) return null
                delay(50)
            } finally {
                player.removeListener(listener)
                player.setSeekParameters(previousSeekParameters)
            }
        }
    }
    val surface = if (isUsingMpvEngine()) mpvView else exoPlayerView?.videoSurfaceView
    if (surface is TextureView) return surface.getBitmap(320, 180)
    if (surface !is SurfaceView || Build.VERSION.SDK_INT < 24 || !surface.holder.surface.isValid) return null
    val height = if (surface.width > 0) (320.0 * surface.height / surface.width).toInt().coerceIn(1, 320) else 180
    val bitmap = Bitmap.createBitmap(320, height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(surface, bitmap, { status ->
                if (continuation.isActive) continuation.resume(if (status == PixelCopy.SUCCESS) bitmap else null)
                // Do not recycle: a cancelled callback can still be writing to this bitmap.
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) { if (continuation.isActive) continuation.resume(null) }
    }
}
