package com.nuvio.tv.data.introdb

import org.junit.Assert.*
import org.junit.Test

class IntroDbReportRulesTest {
    private val tv = ReportMedia("tt0903747", 1, 1)
    @Test fun existingOpeningLeavesOnlyMissingKinds() {
        assertEquals(setOf(ReportSegment.RECAP, ReportSegment.OUTRO),
            IntroDbReportRules.available(tv, setOf(ReportSegment.INTRO)))
    }
    @Test fun allDefinedHidesReporting() {
        assertTrue(IntroDbReportRules.available(tv, ReportSegment.entries.toSet()).isEmpty())
    }
    @Test fun movieHasOnlyCredits() {
        val movie = ReportMedia("tt0371746", movie = true)
        assertEquals(setOf(ReportSegment.OUTRO), IntroDbReportRules.available(movie, emptySet()))
        assertTrue(IntroDbReportRules.available(movie, setOf(ReportSegment.OUTRO)).isEmpty())
    }
    @Test fun rejectsUnresolvedOrSpecialEpisodeIdentity() {
        assertFalse(ReportMedia("tmdb:123", 1, 1).valid)
        assertFalse(ReportMedia("tt0903747", 0, 1).valid)
        assertFalse(ReportMedia("tt0903747", 1, null).valid)
    }
    @Test fun adjustmentCannotCrossBoundariesOrVideoEnd() {
        assertEquals(0L to 1000L, IntroDbReportRules.adjust(0, 1000, 2000, true, -500))
        assertEquals(999L to 1000L, IntroDbReportRules.adjust(0, 1000, 2000, true, 5000))
        assertEquals(500L to 501L, IntroDbReportRules.adjust(500, 1000, 2000, false, -5000))
        assertEquals(500L to 2000L, IntroDbReportRules.adjust(500, 1000, 2000, false, 5000))
    }
    @Test fun rejectsEmptyReverseAndOutOfVideoRanges() {
        assertFalse(IntroDbReportRules.validRange(1000, 500, 2000))
        assertFalse(IntroDbReportRules.validRange(500, 500, 2000))
        assertFalse(IntroDbReportRules.validRange(-1, 500, 2000))
        assertFalse(IntroDbReportRules.validRange(500, 2500, 2000))
        assertTrue(IntroDbReportRules.validRange(0, 2000, 2000))
    }
}
