package com.nuvio.tv.ui.screens.discovery

import org.junit.Assert.*
import org.junit.Test

class DiscoveryPolicyTest {
    @Test fun prefetchStartsOnPenultimateRowAndNeverForToolbarFocus() {
        assertFalse(discoveryShouldPrefetch(-1,20,6))
        assertFalse(discoveryShouldPrefetch(7,20,6))
        assertTrue(discoveryShouldPrefetch(8,20,6))
        assertTrue(discoveryShouldPrefetch(18,20,1))
        assertFalse(discoveryShouldPrefetch(17,20,1))
        assertFalse(discoveryShouldPrefetch(0,0,6))
    }
    @Test fun multipleGenresRequireAllButActorsCanMatchAny() {
        val filters=DiscoveryFilters(genres=setOf(878,28),actors=listOf(DiscoveryChoice("31","A"),DiscoveryChoice("32","B")),allActors=false)
        val query=filters.query(movie=true)
        assertEquals("28,878",query["with_genres"])
        assertEquals("31|32",query["with_cast"])
        assertEquals("31,32",filters.copy(allActors=true).query(true)["with_cast"])
    }
    @Test fun televisionQueryNeverLeaksMovieOnlyFilters() {
        val query=DiscoveryFilters(actors=listOf(DiscoveryChoice("31","A")),certification="PG",status="3",yearFrom=1990,yearTo=1999).query(false)
        assertFalse(query.containsKey("with_cast"))
        assertFalse(query.containsKey("certification"))
        assertEquals("3",query["with_status"])
        assertEquals("1990-01-01",query["first_air_date.gte"])
        assertEquals("1999-12-31",query["first_air_date.lte"])
    }
    @Test fun movieCertificationHasExplicitCountryAndSeriesStatusIsOmitted() {
        val query=DiscoveryFilters(certification="PG-13",status="3").query(true)
        assertEquals("US",query["certification_country"])
        assertFalse(query.containsKey("with_status"))
    }
    @Test fun unsetFiltersNeverBecomeAccidentalEmptyServerConstraints() {
        assertTrue(DiscoveryFilters().query(true).isEmpty())
        assertFalse(DiscoveryFilters(allActors=false).active)
        assertTrue(DiscoveryFilters(country="IL").active)
    }
    @Test fun singleSelectionsSerializeToSingleIds() {
        val query=DiscoveryFilters(company=DiscoveryChoice("174","Warner Bros."),country="IL",language="he",keyword=DiscoveryChoice("4379","Time travel")).query(true)
        assertEquals("174",query["with_companies"])
        assertEquals("IL",query["with_origin_country"])
        assertEquals("he",query["with_original_language"])
    }
    @Test fun runtimeParsesMinutesHoursAndIsoDurationWithoutConcatenatingDigits() {
        assertEquals(130,discoveryRuntimeMinutes("2h 10m"))
        assertEquals(130,discoveryRuntimeMinutes("PT2H10M"))
        assertEquals(90,discoveryRuntimeMinutes("90m"))
        assertEquals(90,discoveryRuntimeMinutes("90"))
        assertNull(discoveryRuntimeMinutes("unknown"))
        assertNull(discoveryRuntimeMinutes(null))
    }
    @Test fun sixViewsCycleBackToStart() {
        var view=DiscoveryView.POSTERS
        val seen=mutableSetOf<DiscoveryView>()
        repeat(6){seen+=view;view=view.next()}
        assertEquals(6,seen.size)
        assertEquals(DiscoveryView.POSTERS,view)
    }
}
