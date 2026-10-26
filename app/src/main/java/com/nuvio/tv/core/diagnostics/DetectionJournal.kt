package com.nuvio.tv.core.diagnostics

/** Only fixed events/outcomes and numeric metrics can enter an uploadable log. */
internal class DetectionJournal {
    private val lines = ArrayDeque<String>()
    private var startedAt = 0L
    private val metrics = setOf("cue_count", "body_chars", "proposal_count", "frame_count", "duration_ms",
        "http_status", "cloud_enabled", "review_mode", "missing_count", "subtitle_selected")
    private val events = setOf("session", "status", "lookup_result", "subtitle_download", "subtitle_parsed",
        "cloud_request", "cloud_result", "media_sample", "review_offered", "failure", "settings")

    @Synchronized fun begin(now: Long) { lines.clear(); startedAt = now; add("session", "waiting", emptyMap(), now) }

    @Synchronized fun add(event: String, status: String, values: Map<String, Long>, now: Long) {
        if (startedAt == 0L || event !in events) return
        val fields = values.filterKeys { it in metrics }.toSortedMap().entries.joinToString(" ") {
            "${it.key}=${it.value.coerceIn(0L, 86_400_000L)}"
        }
        lines.addLast("t_ms=${(now - startedAt).coerceAtLeast(0)} event=$event status=${YpCrashReportText.safeDetectionStatus(status)} $fields".trim())
        while (lines.size > 120 || lines.sumOf { it.length + 1 } > 20_000) lines.removeFirst()
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
