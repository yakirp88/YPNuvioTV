package com.nuvio.tv.ui.screens.player

import kotlinx.coroutines.CancellationException

/** Optional evidence collection must not fail the player's coroutine scope. */
internal suspend fun runIntroDetectionSafely(
    onFailure: (Exception) -> Unit,
    collectEvidence: suspend () -> Unit
) {
    try {
        collectEvidence()
    } catch (cancelled: CancellationException) {
        // Leaving the player/profile must still stop all obsolete work.
        throw cancelled
    } catch (error: Exception) {
        // Do not log exception messages: subtitle/stream URLs can contain credentials.
        onFailure(error)
    }
}
