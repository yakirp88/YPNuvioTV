package com.nuvio.tv.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YpCrashReportTextTest {
    @Test fun `live snapshot and translated panel expose only safe fixed data`() {
        val journal = DetectionJournal()
        journal.begin(1000)
        journal.add("media_sample", "waiting", mapOf("frame_count" to 24L, "private-key" to 7L), 2000)
        val snapshot = journal.snapshot()
        val text = com.nuvio.tv.ui.screens.player.IntroDebugText.entry(snapshot.entries.last(), true)
        assertTrue(text.contains("24"))
        assertFalse(text.contains("private-key"))
        journal.reset()
        assertTrue(journal.snapshot().entries.isEmpty())
        assertTrue(snapshot.entries.isNotEmpty())
        assertFalse(com.nuvio.tv.ui.screens.player.IntroDebugText.status("https://secret?key=private", true).contains("secret"))
        repeat(500) { journal.begin(3000L + it) }
        assertEquals(1, journal.snapshot().entries.size)
    }

    @Test
    fun `review rebinds cannot restart the playback journal or erase its failure`() {
        val journal = DetectionJournal()
        var resets = 0
        val session = DetectionJournalSession { resets++; journal.begin(1000) }
        session.ensureStarted()
        journal.add("failure", "network_timeout", emptyMap(), 1100)
        repeat(5) { session.ensureStarted() }
        assertEquals(1, resets)
        assertTrue(journal.text().contains("network_timeout"))
        val next = DetectionJournalSession { journal.begin(2000) }
        next.ensureStarted()
        assertFalse(journal.text().contains("network_timeout"))
    }

    @Test
    fun `cloud failures expose fixed categories without exception messages`() {
        val failure = com.nuvio.tv.core.intro.GeminiIntroAnalyzer.Failure(
            com.nuvio.tv.core.intro.GeminiIntroAnalyzer.FailureKind.DNS)
        assertEquals("dns_failed", com.nuvio.tv.ui.screens.player.cloudFailureStatus(failure))
        assertEquals("output_invalid", com.nuvio.tv.ui.screens.player.cloudFailureStatus(IllegalArgumentException("secret-key")))
        assertEquals("cloud_failed", com.nuvio.tv.ui.screens.player.cloudFailureStatus(RuntimeException("https://private")))
        assertEquals("media_unavailable", YpCrashReportText.safeDetectionStatus("media_unavailable"))
    }

    @Test
    fun `extended journal is bounded and cannot accept free text or credential fields`() {
        val journal = DetectionJournal()
        journal.begin(1000)
        journal.add("subtitle_parsed", "subtitles", mapOf("cue_count" to 17L, "secret_key" to 1234L), 1100)
        assertTrue(journal.text().contains("cue_count=17"))
        assertFalse(journal.text().contains("secret_key"))
        journal.add("https://private?key=secret", "secret", emptyMap(), 1200)
        repeat(500) { journal.add("status", "http_403", emptyMap(), 1300L + it) }
        assertTrue(journal.text().lines().size <= 122)
        assertTrue(journal.text().length < 24000)
        assertFalse(journal.text().contains("secret"))
        journal.begin(5000)
        assertFalse(journal.text().contains("http_403"))
    }

    @Test
    fun `upload accepts only public paste links on the selected provider`() {
        assertTrue(DetectionLogUpload.validLink("https://dpaste.org/Ab12cd"))
        assertFalse(DetectionLogUpload.validLink("https://evil.example/Ab12cd"))
        assertFalse(DetectionLogUpload.validLink("https://dpaste.org/Ab12cd?key=secret"))
    }

    @Test
    fun `detection status accepts fixed outcomes and HTTP codes only`() {
        assertEquals("http_403", YpCrashReportText.safeDetectionStatus("http_403"))
        assertEquals("no_proposals", YpCrashReportText.safeDetectionStatus("no_proposals"))
        assertEquals("analysis_failed", YpCrashReportText.safeDetectionStatus("AIza_private_key"))
        assertEquals("analysis_failed", YpCrashReportText.safeDetectionStatus("https://stream?token=secret"))
    }

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
