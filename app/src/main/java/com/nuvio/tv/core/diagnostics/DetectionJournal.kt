package com.nuvio.tv.core.diagnostics

internal data class DetectionEntry(val elapsedMs: Long, val event: String, val status: String, val metrics: Map<String, Long>)
internal data class DetectionSnapshot(val startedAt: Long = 0, val entries: List<DetectionEntry> = emptyList())

/** Only fixed events/outcomes and numeric metrics can enter an uploadable log. */
internal class DetectionJournal {
    private val lines = ArrayDeque<String>()
    private val entries = ArrayDeque<DetectionEntry>()
    private var startedAt = 0L
    private val metrics = setOf("cue_count", "body_chars", "proposal_count", "frame_count", "duration_ms",
        "http_status", "cloud_enabled", "review_mode", "missing_count", "subtitle_selected", "attempt", "model_code", "candidate_count", "embedded_count", "rejected_count", "source_code", "sdh_count", "discovered_count", "language_count", "checked_count")
    private val events = setOf("session", "status", "lookup_result", "subtitle_download", "subtitle_parsed",
        "cloud_request", "cloud_result", "media_sample", "review_offered", "failure", "settings", "cloud_retry", "provider_reason", "cloud_probe_request", "cloud_probe_result", "subtitle_provider", "embedded_scan", "embedded_subtitles", "subtitle_search", "subtitle_choice", "subtitle_candidate", "cloud_model", "probe_model", "cloud_fallback")

    @Synchronized fun reset() { lines.clear(); entries.clear(); startedAt = 0 }
    @Synchronized fun snapshot() = DetectionSnapshot(startedAt, entries.toList())
    @Synchronized fun begin(now: Long) { reset(); startedAt = now; add("session", "waiting", emptyMap(), now) }

    @Synchronized fun add(event: String, status: String, values: Map<String, Long>, now: Long) {
        if (startedAt == 0L || event !in events) return
        val safeValues = values.filterKeys { it in metrics }.mapValues { it.value.coerceIn(0L, 86_400_000L) }
        entries.addLast(DetectionEntry((now - startedAt).coerceAtLeast(0), event, YpCrashReportText.safeDetectionStatus(status), safeValues.toMap()))
        val fields = safeValues.toSortedMap().entries.joinToString(" ") {
            "${it.key}=${it.value.coerceIn(0L, 86_400_000L)}"
        }
        lines.addLast("t_ms=${(now - startedAt).coerceAtLeast(0)} event=$event status=${YpCrashReportText.safeDetectionStatus(status)} $fields".trim())
        while (lines.size > 120 || lines.sumOf { it.length + 1 } > 20_000) { lines.removeFirst(); entries.removeFirst() }
    }

    @Synchronized fun text(): String = "NUVIO YP DETECTION LOG\nsession_time_ms=$startedAt\n" + lines.joinToString("\n")
}

/** Owned by one playback collector, independently of transient review/timeline identities. */
internal class DetectionJournalSession(private val begin: () -> Unit) {
    private var started = false
    fun ensureStarted() {
        if (started) return
        started = true
        begin()
    }
}
