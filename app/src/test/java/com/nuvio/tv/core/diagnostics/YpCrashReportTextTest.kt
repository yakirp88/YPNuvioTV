package com.nuvio.tv.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpCrashReportTextTest {
    @Test
    fun `report keeps actionable frames but drops messages and suppressed credentials`() {
        val cause = IllegalArgumentException("https://stream.example/video?token=private-secret")
        val error = IllegalStateException("AIza-private-key", cause)
        error.addSuppressed(IllegalStateException("Bearer private-secret"))
        error.stackTrace = arrayOf(StackTraceElement("com.nuvio.tv.Player", "open", "Player.kt", 42))
        val text = YpCrashReportText.failure("uncaught", "intro_lookup", error, 1)
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("Player.open(Player.kt:42)"))
        assertTrue(text.contains("stage=intro_lookup"))
        assertFalse(text.contains("private-secret"))
        assertFalse(text.contains("AIza"))
        assertFalse(text.contains("Bearer"))
        assertFalse(text.contains("https://"))
    }

    @Test
    fun `untrusted stage and source-file strings cannot disclose paths or credentials`() {
        val error = RuntimeException()
        error.stackTrace = arrayOf(StackTraceElement("com.nuvio.tv.Player", "open", "/private/token=secret.kt", 7))
        val text = YpCrashReportText.failure("uncaught", "https://private?key=secret", error, 1)
        assertTrue(text.contains("stage=unknown"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("/private"))
    }

    @Test
    fun `cyclic causes and long stacks stay bounded`() {
        val first = RuntimeException()
        val second = RuntimeException(first)
        first.initCause(second)
        first.stackTrace = Array(10000) { StackTraceElement("com.nuvio.tv.Player", "open", "Player.kt", it) }
        val text = YpCrashReportText.failure("uncaught", "intro_failed", first, 1)
        assertTrue(text.length < 7200)
        assertEquals(1, Regex("cause\\[0\\]").findAll(text).count())
        assertFalse(text.contains("cause[2]"))
    }

    @Test
    fun `pages fit a TV screenshot and retain bounded report content`() {
        val input = (1..30).joinToString("\n") { "line$it " + "x".repeat(100) }
        val pages = YpCrashReportText.pages(input)
        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.lines().size <= 8 && it.lines().all { line -> line.length <= 78 } })
        assertTrue(pages.last().contains("line30"))
    }
}
