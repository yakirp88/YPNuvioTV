package com.nuvio.tv.core.diagnostics

import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap

/** Allowlisted structural data only: never Throwable.message/toString or raw logcat. */
internal object YpCrashReportText {
    private val token = Regex("[A-Za-z0-9_.$<>-]{1,160}")
    private val stages = setOf("app_start", "player_ready", "intro_lookup", "intro_subtitles", "intro_cloud", "intro_media", "intro_complete", "intro_failed")
    fun safeToken(value: String): String = value.takeIf { token.matches(it) } ?: "redacted"
    fun safeStage(value: String): String = value.takeIf { it in stages } ?: "unknown"

    fun failure(kind: String, stage: String, error: Throwable, timestamp: Long): String {
        val body = buildString {
            appendLine("kind=${safeToken(kind)}")
            appendLine("time_ms=$timestamp")
            appendLine("stage=${safeStage(stage)}")
            val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            var current: Throwable? = error
            repeat(4) { causeIndex ->
                val cause = current ?: return@repeat
                if (!seen.add(cause)) { current = null; return@repeat }
                appendLine("cause[$causeIndex]=${safeToken(cause.javaClass.name)}")
                cause.stackTrace.take(12).forEach { frame ->
                    appendLine("  ${safeToken(frame.className)}.${safeToken(frame.methodName)}(${safeToken(frame.fileName ?: "Unknown")}:${frame.lineNumber})")
                }
                current = cause.cause
            }
        }.take(7000)
        val id = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
            .take(6).joinToString("") { "%02x".format(it) }
        return "YP report $id\n$body"
    }

    /** Fixed-width, bounded pages fit a TV screenshot without scrolling through a stack. */
    fun pages(text: String): List<String> = text.take(24000).lineSequence()
        .flatMap { if (it.isEmpty()) sequenceOf("") else it.chunked(78).asSequence() }
        .toList().chunked(8).map { it.joinToString("\n") }.ifEmpty { listOf("No report") }
}
