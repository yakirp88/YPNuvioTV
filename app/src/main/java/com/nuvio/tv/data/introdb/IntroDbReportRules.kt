package com.nuvio.tv.data.introdb

/** Pure policy, shared by the UI and submission path. */
enum class ReportSegment(val apiName: String) { INTRO("intro"), RECAP("recap"), OUTRO("outro") }

data class ReportMedia(val imdbId: String, val season: Int? = null, val episode: Int? = null, val movie: Boolean = false) {
    val key: String get() = "$imdbId:${if (movie) "movie" else "$season:$episode"}"
    val valid: Boolean get() = Regex("tt[0-9]{7,8}").matches(imdbId) &&
        (movie || (season != null && season >= 1 && episode != null && episode > 0))
}

object IntroDbReportRules {
    fun available(media: ReportMedia, existing: Set<ReportSegment>): Set<ReportSegment> =
        (if (media.movie) setOf(ReportSegment.OUTRO) else ReportSegment.entries.toSet()) - existing

    fun validRange(startMs: Long, endMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && startMs >= 0 && endMs > startMs && endMs <= durationMs

    fun adjust(startMs: Long, endMs: Long, durationMs: Long, start: Boolean, deltaMs: Long): Pair<Long, Long> {
        if (!validRange(startMs, endMs, durationMs)) return startMs to endMs
        return if (start) (startMs + deltaMs).coerceIn(0, endMs - 1) to endMs
        else startMs to (endMs + deltaMs).coerceIn(startMs + 1, durationMs)
    }
}
