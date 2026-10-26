package com.nuvio.tv.core.intro

import com.nuvio.tv.core.diagnostics.YpCrashDiagnostics
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Two documented, fixed Google models. No provider/endpoint changes or auth/quota fallback. */
internal object GeminiModelRoute {
    suspend fun <T> run(probe: Boolean = false, pause: suspend (Long) -> Unit = { delay(it) },
        note: (String, Long, Int, Int) -> Unit = { event, model, attempt, status ->
            YpCrashDiagnostics.logDetectionEvent(event, "waiting", mapOf("model_code" to model, "attempt" to attempt.toLong(), "http_status" to status.toLong()))
        }, action: suspend (String) -> T): T {
        try {
            return withTimeout(if (probe) 65_000 else 240_000) {
                val models = if (probe) listOf("gemini-3.8-flash", "gemini-3.6-flash") else listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.6-flash")
                for ((index, model) in models.withIndex()) {
                    val code = if (model == "gemini-3.8-flash") 38L else 36L
                    note(if (probe) "probe_model" else "cloud_model", code, index + 1, 0)
                    try { return@withTimeout action(model) }
                    catch (error: GeminiIntroAnalyzer.HttpFailure) {
                        if (error.status !in setOf(502, 503, 504) || index == models.lastIndex) throw error
                        note("cloud_fallback", 36L, index + 2, error.status)
                        pause((1L shl index) * 1000 + kotlin.random.Random.nextLong(0, 501))
                    }
                }
                error("No model result")
            }
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw GeminiIntroAnalyzer.Failure(GeminiIntroAnalyzer.FailureKind.TIMEOUT)
        }
    }
}
