package com.nuvio.tv.core.intro

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class IntroAcquisitionTest {

    @Test fun independentSearchPreservesSdhMetadataAndDoesNotRequireEnglishOrInstalledAddons() {
        val found = PublicIntroSubtitles.parse("""{"subtitles":[
            {"id":"numeric-1","url":"https://example.org/1.srt","lang":"jpn","label":"Japanese [SDH]"},
            {"id":"numeric-2","url":"https://example.org/2.srt","lang":"spa","hearing_impaired":true},
            {"id":"numeric-3","url":"https://example.org/3.srt","lang":"fra","hearingImpaired":1},
            {"id":"numeric-4","url":"http://example.org/4.srt","lang":"eng"}
        ]}""")
        assertEquals(3, found.size)
        assertTrue(found.all { IntroSubtitlePolicy.descriptive(it.label) })
        assertEquals(setOf("jpn", "spa", "fra"), IntroSubtitleSearch.candidates(found, emptyList()).map { it.subtitle.lang }.toSet())
        assertEquals("numeric-1", found.first().subtitle.id)
    }

    @Test fun independentSdhResultsAreNotStarvedByInstalledOrFirstLanguageResults() {
        fun option(id: Int, lang: String, label: String = "ordinary") = IntroSubtitleOption(
            com.nuvio.tv.domain.model.Subtitle("$id", "https://example.org/$id.srt", lang, "test", null), label)
        val independent = (0 until 60).map { option(it, "eng") } + listOf(
            option(60, "jpn", "SDH"), option(61, "fra"), option(62, "spa"))
        val installed = (100 until 150).map { option(it, "heb") }
        val pool = IntroSubtitleSearch.candidates(independent, installed)
        assertEquals(32, pool.size)
        assertEquals("60", pool.first().subtitle.id)
        assertTrue(pool.any { it.subtitle.lang == "fra" })
        assertTrue(pool.any { it.subtitle.lang == "spa" })
        assertEquals(24, pool.count { it.subtitle.addonName == "test" && it.subtitle.id.toInt() < 100 })
        assertEquals(pool.size, pool.map { it.subtitle.url }.distinct().size)
    }
    @Test fun indexedMatroskaTextUsesCueOffsetsAndChecksTrackAndTimestamp() {
        val time = byteArrayOf(0xE7.toByte(), 0x82.toByte(), 0x03, 0xE8.toByte())
        val payload = byteArrayOf(0x81.toByte(), 0, 10, 0) + "[Theme music]".toByteArray()
        val block = byteArrayOf(0xA3.toByte(), (0x80 or payload.size).toByte()) + payload
        val content = time + block
        val bytes = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75, (0x80 or content.size).toByte()) + content + ByteArray(32)
        val point = EmbeddedTextReadPoint(1010, 0, 4, null)
        val source = EmbeddedTextReadSource(1, "S_TEXT/UTF8", "SDH", 0, 1_000_000, listOf(point))
        fun read(at: Long, n: Int) = bytes.copyOfRange(at.toInt(), at.toInt() + n)
        val cue = IndexedIntroSubtitles.decodePoint(source, point, ::read)
        assertNotNull(cue)
        assertEquals("[Theme music]", cue!!.text)
        assertEquals(1010L, cue.startMs)
        assertEquals(1011L, cue.endMs)
        assertNull(IndexedIntroSubtitles.decodePoint(source.copy(track = 2), point, ::read))
        assertNull(IndexedIntroSubtitles.decodePoint(source, point.copy(timeMs = 3000), ::read))
        assertNull(IndexedIntroSubtitles.decodePoint(source, point.copy(clusterPosition = Long.MAX_VALUE), ::read))
    }

    @Test fun descriptiveSubtitlesAndNativePacketDecoding() {
        assertTrue(IntroSubtitlePolicy.descriptive("English SDH"))
        assertFalse(IntroSubtitlePolicy.descriptive("Chinese"))
        assertEquals("[MUSIC]", IntroSubtitlePolicy.decode(byteArrayOf(0, 7) + "[MUSIC]".toByteArray(), "text/3gpp"))
        assertNull(IntroSubtitlePolicy.decode(byteArrayOf(0, 40, 65), "text/3gpp"))
        assertNull(IntroSubtitlePolicy.decode(byteArrayOf(-1, -2), "application/x-subrip"))
        assertEquals("[Music]\nTheme", IntroSubtitlePolicy.decode("0,0,Default,,0,0,0,,[Music]\\NTheme".toByteArray(), "text/x-ass"))
    }

    @Test fun timestampConsensusRecoversDelayAndRejectsUnrelatedCues() {
        val times = (0 until 120).map { i -> i * 8000L + (i * i * 137L % 2700) }
        val cues = times.map { SubtitleEvidenceAnalyzer.Cue(it + 4500, it + 5500, "dialogue") }
        assertEquals(-4500L, IntroSubtitlePolicy.alignOffset(cues, times, 1_000_000))
        val sdh = (cues + times.map { SubtitleEvidenceAnalyzer.Cue(it + 6200, it + 7200, "[MUSIC]") }).sortedBy { it.startMs }
        assertEquals(-4500L, IntroSubtitlePolicy.alignOffset(sdh, times, 1_000_000))
        val other = (0 until 150).map { i -> i * 6317L + (i * i * 911L % 5300) }
        assertNull(IntroSubtitlePolicy.alignOffset(cues, other, 1_000_000))
    }

    @Test fun rangeReaderCachesAndRejectsServersThatIgnoreRanges() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-1/2").setBody("ok"))
            IntroRangeReader(server.url("/").toString(), emptyMap(), { true }).use { reader ->
                assertEquals(2L, reader.getSize())
                val b = ByteArray(2)
                assertEquals(2, reader.readAt(0, b, 0, 2))
                assertEquals("ok", b.toString(Charsets.UTF_8))
                assertEquals(-1, reader.readAt(2, b, 0, 2))
                assertEquals(1, reader.requests)
            }
            assertEquals("bytes=0-262143", server.takeRequest().getHeader("Range"))
            server.enqueue(MockResponse().setBody("whole movie must not be downloaded"))
            IntroRangeReader(server.url("/").toString(), emptyMap(), { true }).use { reader ->
                try { reader.getSize(); fail("Must refuse non-partial response") } catch (_: IOException) { }
            }
        }
    }

    @Test fun rangeBudgetAndCancellationPreventAdditionalRequests() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-1/1000000").setBody("ok"))
            var active = true
            IntroRangeReader(server.url("/").toString(), emptyMap(), { active }, maxRequests = 1).use { reader ->
                reader.getSize()
                try { reader.readAt(262144, ByteArray(1), 0, 1); fail("Budget must stop new requests") } catch (_: IOException) { }
                active = false
                try { reader.readAt(0, ByteArray(1), 0, 1); fail("Cancellation must stop cached reads too") } catch (_: IOException) { }
                assertEquals(1, reader.requests)
            }
        }
    }

    @Test fun unavailablePrimaryModelFallsBackToDocumentedSecondary() = runBlocking {
        val models = mutableListOf<String>()
        val result = GeminiModelRoute.run(probe = true, pause = {}, note = { _, _, _, _ -> }) { model ->
            models += model
            if (models.size == 1) throw GeminiIntroAnalyzer.HttpFailure(503)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.6-flash"), models)
    }

    @Test fun authAndQuotaFailuresNeverSwitchModels() = runBlocking {
        for (status in listOf(400, 403, 429)) {
            var calls = 0
            try {
                GeminiModelRoute.run<String>(pause = {}, note = { _, _, _, _ -> }) { calls++; throw GeminiIntroAnalyzer.HttpFailure(status) }
                fail("Expected provider failure")
            } catch (e: GeminiIntroAnalyzer.HttpFailure) { assertEquals(status, e.status) }
            assertEquals(1, calls)
        }
    }

    @Test fun modelAttemptsAreBoundedAndCancellationPropagates() = runBlocking {
        var calls = 0
        try {
            GeminiModelRoute.run<String>(pause = {}, note = { _, _, _, _ -> }) { calls++; throw GeminiIntroAnalyzer.HttpFailure(503) }
            fail("Expected final failure")
        } catch (_: GeminiIntroAnalyzer.HttpFailure) { }
        assertEquals(3, calls)
        try {
            GeminiModelRoute.run<String>(pause = {}, note = { _, _, _, _ -> }) { throw CancellationException() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
