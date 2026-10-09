package com.nuvio.tv.data.introdb

import org.junit.Assert.*
import org.junit.Test

class IntroDbReportRulesTest {
    private val tv = ReportMedia("tt0903747", 1, 1)
    @Test fun remoteTapAndHoldAccelerateInBothDirections() {
        assertEquals(500L, IntroDbReportRules.calibrationDelta(0, true))
        assertEquals(-500L, IntroDbReportRules.calibrationDelta(0, false))
        assertEquals(2000L, IntroDbReportRules.calibrationDelta(1, true))
        assertEquals(10000L, IntroDbReportRules.calibrationDelta(8, true))
        assertEquals(-30000L, IntroDbReportRules.calibrationDelta(20, false))
    }
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
    @Test fun minuteAdjustmentsPreserveOrderingAndClampAtVideoBounds() {
        for (step in listOf(60_000L, 300_000L, 600_000L)) {
            assertEquals(600_000L to 1_200_000L + step,
                IntroDbReportRules.adjust(600_000L, 1_200_000L, 3_600_000L, false, step))
            assertEquals(600_000L - step to 1_200_000L,
                IntroDbReportRules.adjust(600_000L, 1_200_000L, 3_600_000L, true, -step))
            assertEquals(600_000L to 600_001L,
                IntroDbReportRules.adjust(600_000L, 601_000L, 3_600_000L, false, -step))
            assertEquals(0L to 1_200_000L,
                IntroDbReportRules.adjust(10_000L, 1_200_000L, 3_600_000L, true, -step))
        }
    }
}
