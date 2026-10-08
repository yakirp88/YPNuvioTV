package com.nuvio.tv.data.introdb

class IntroDbReportRepository {
    var enabled = true
    var existingTypes = emptySet<ReportSegment>()
    var failRead = false
    var failSend = false
    var sends = 0
    var reads = 0
    var sentStart = -1L
    var sentEnd = -1L
    var key: String? = "idb_test"
    private val submitted = mutableSetOf<ReportSegment>()
    data class Draft(val segment: ReportSegment, val start: Long, val end: Long, val duration: Long)
    private var savedDraft: Draft? = null
    fun apiKey() = key
    fun saveApiKey(value: String) { key = value }
    suspend fun existing(media: ReportMedia): Set<ReportSegment> { reads++; check(!failRead); return existingTypes }
    suspend fun submit(media: ReportMedia, segment: ReportSegment, start: Long, end: Long, duration: Long): Boolean {
        if (segment in existing(media)) return false
        check(!failSend)
        sends++; sentStart = start; sentEnd = end
        return true
    }
    fun submitted(media: ReportMedia) = submitted.toSet()
    fun markSubmitted(media: ReportMedia, segment: ReportSegment) { submitted += segment }
    fun draft(media: ReportMedia) = savedDraft
    fun saveDraft(media: ReportMedia, segment: ReportSegment, start: Long, end: Long, duration: Long) {
        savedDraft = Draft(segment, start, end, duration)
    }
    fun removeDraft(media: ReportMedia) { savedDraft = null }
}
