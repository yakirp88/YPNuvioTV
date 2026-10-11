package com.nuvio.tv.ui.screens.discovery

import org.junit.Assert.*
import org.junit.Test

class DiscoveryPolicyTest {
    @Test fun newestDecadeComesFirst() {
        assertEquals(2020,discoveryDecades(2026).first())
        assertEquals(2030,discoveryDecades(2031).first())
        assertEquals(1950,discoveryDecades(2026).last())
    }
    @Test fun voteCheckboxUsesSameBoundaryForMoviesAndSeriesAndRejectsUnknownCounts() {
        val f=DiscoveryFilters(votes=DISCOVERY_MIN_VOTES)
        assertEquals("100",f.query(true)["vote_count.gte"])
        assertEquals("100",f.query(false)["vote_count.gte"])
        assertFalse(discoveryVoteMatches(99,f.votes))
        assertTrue(discoveryVoteMatches(100,f.votes))
        assertFalse(discoveryVoteMatches(null,f.votes))
        assertTrue(discoveryVoteMatches(null,null))
    }
    @Test fun addingCatalogToListPreservesExistingMemberships() {
        val existing=mapOf("watchlist" to true,"other" to true,"destination" to false)
        val result=discoveryAddedMembership(existing,"destination")
        assertEquals(true,result["watchlist"]);assertEquals(true,result["other"])
        assertEquals(true,result["destination"])
        assertEquals(false,existing["destination"])
        assertEquals(result,discoveryAddedMembership(result,"destination"))
    }
    @Test fun informationDefaultsToTopAndDelayIsBounded() {
        assertEquals(DiscoveryInfoPosition.TOP,discoveryInfoPosition(null))
        assertEquals(DiscoveryInfoPosition.TOP,discoveryInfoPosition("invalid"))
        assertEquals(DiscoveryInfoPosition.TOP,discoveryInfoPosition("MIDDLE"))
        assertEquals(DiscoveryInfoPosition.TOP,discoveryInfoPosition("EXPAND"))
        assertEquals(DiscoveryInfoPosition.SIDE,discoveryInfoPosition("SIDE"))
    }
    @Test fun genreCycleIsExclusiveAndReturnsToNeutral() {
        val included=DiscoveryFilters().cycleGenre(28)
        assertEquals(setOf(28),included.genres);assertTrue(included.excludedGenres.isEmpty())
        val excluded=included.cycleGenre(28)
        assertTrue(excluded.genres.isEmpty());assertEquals(setOf(28),excluded.excludedGenres)
        assertEquals(DiscoveryFilters(),excluded.cycleGenre(28))
    }
    @Test fun builtinPopularAndNewCatalogsSendFiltersButTrendingStaysLocal() {
        assertTrue(discoveryUsesServerFilters(null,null,false))
        assertTrue(discoveryUsesServerFilters("popular","nuvio.tmdb",false))
        assertTrue(discoveryUsesServerFilters("new","nuvio.tmdb",false))
        assertFalse(discoveryUsesServerFilters("trending","nuvio.tmdb",false))
        assertFalse(discoveryUsesServerFilters("popular","addon",false))
        assertFalse(discoveryUsesServerFilters(null,null,true))
    }

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
        assertTrue(DiscoveryFilters(votes=null,releasedOnly=false).query(true).isEmpty())
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
    @Test fun fourCardStylesCycleBackToStart() {
        var view=DiscoveryView.POSTERS
        val seen=mutableSetOf<DiscoveryView>()
        repeat(4){seen+=view;view=view.next()}
        assertEquals(4,seen.size)
        assertEquals(DiscoveryView.POSTERS,view)
    }
    @Test fun everyStyleHasFiveDistinctSizes() {
        for(side in listOf(false,true)) for(style in DiscoveryView.entries) {
            val columns=(0..4).map {discoveryColumns(style,it,side)}
            assertEquals(5,columns.distinct().size)
            assertTrue(columns.all {it>=3})
            assertEquals(columns.last(),discoveryColumns(style,20,side))

        }
    }
    @Test fun decadesFillBothBoundsAndPreserveOtherFilters() {
        val f=DiscoveryFilters(country="IL",genres=setOf(28))
        assertEquals(f.copy(yearFrom=1990,yearTo=1999),discoveryDecade(f,1990))
    }
    @Test fun appendedExternalIdsWithoutNestedIdDoNotDiscardLogosOrMetadata() {
        val moshi=com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        val payload="""{"id":438631,"title":"Dune","runtime":155,"external_ids":{"imdb_id":"tt1160419"},"images":{"logos":[{"file_path":"/dune.png","iso_639_1":"en"}]}}"""
        val details=moshi.adapter(com.nuvio.tv.data.remote.api.TmdbDetailsResponse::class.java).fromJson(payload)!!
        assertEquals("tt1160419",details.externalIds?.imdbId)
        assertEquals("/dune.png",details.images?.logos?.single()?.filePath)
        assertEquals(155,details.runtime)
    }
    @Test fun heroUsesHighResolutionWithoutRewritingAddonArtwork() {
        assertEquals("https://image.tmdb.org/t/p/w1280/scene.jpg",discoveryHeroImageUrl("https://image.tmdb.org/t/p/w500/scene.jpg"))
        assertEquals("https://addon.test/scene.jpg",discoveryHeroImageUrl("https://addon.test/scene.jpg"))
        assertNull(discoveryHeroImageUrl(null))
        assertEquals("https://images.metahub.space/logo/medium/tt1160419/img",discoveryFallbackLogo("tt1160419"))
        assertNull(discoveryFallbackLogo("tmdb:438631"))
    }
    @Test fun removedViewsMigrateToModernCardStyles() {
        assertEquals(DiscoveryView.POSTERS,discoveryCardStyle("LIST"))
        assertEquals(DiscoveryView.LANDSCAPE,discoveryCardStyle("CARDS"))
        assertEquals(DiscoveryView.CLEAR_LOGO,discoveryCardStyle("CLEAR_LOGO"))
    }
    @Test fun logoArtworkUsesOriginalSizeIncludingSvgAndPreservesAddonUrls() {
        assertEquals("https://image.tmdb.org/t/p/original/logo.svg",discoveryLogoUrl("/logo.svg"))
        assertEquals("https://image.tmdb.org/t/p/original/logo.png",discoveryLogoUrl("/logo.png"))
        assertEquals("https://addon/logo.png",discoveryLogoUrl("https://addon/logo.png"))
        assertNull(discoveryLogoUrl(""))
    }

    @Test fun baselineFiltersResetOnAndNeverBecomeActiveChips() {
        val defaults=DiscoveryFilters()
        assertEquals(100,defaults.votes);assertTrue(defaults.releasedOnly);assertFalse(defaults.active)
        assertFalse(defaults.copy(votes=null,releasedOnly=false).active)
        assertTrue(defaults.copy(country="IL").active)
    }
    @Test fun actorQuerySurvivesEverySortAndIncludeMissingWithExactPaging() {
        val f=DiscoveryFilters(actors=listOf(DiscoveryChoice("976","Jason Statham")))
        val today=java.time.LocalDate.of(2026,10,11)
        for(missing in listOf(false,true)) for(descending in listOf(false,true)) for(sort in DiscoverySort.entries) {
            val query=discoveryServerQuery(true,f,sort,descending,2,"he",missing,today=today)
            assertEquals("976",query["with_cast"]);assertEquals("2",query["page"])
            assertEquals("100",query["vote_count.gte"]);assertEquals("2026-10-11",query["primary_release_date.lte"])
            assertTrue(query.getValue("sort_by").endsWith(if(descending) ".desc" else ".asc"))
        }
    }
    @Test fun releasedFilterIntersectsYearRangeAndUsesFirstAirDateForSeries() {
        val today=java.time.LocalDate.of(2026,10,11)
        assertEquals("2026-10-11",DiscoveryFilters(yearTo=2030).query(true,today)["primary_release_date.lte"])
        assertEquals("1999-12-31",DiscoveryFilters(yearTo=1999).query(true,today)["primary_release_date.lte"])
        assertEquals("2026-10-11",DiscoveryFilters().query(false,today)["first_air_date.lte"])
        assertFalse(DiscoveryFilters(releasedOnly=false).query(true,today).containsKey("primary_release_date.lte"))
        assertTrue(discoveryReleasedMatches("2026-10-11",true,today))
        assertFalse(discoveryReleasedMatches("2026-10-12",true,today))
        assertFalse(discoveryReleasedMatches(null,true,today));assertFalse(discoveryReleasedMatches("invalid",true,today))
        assertTrue(discoveryReleasedMatches(null,false,today))
    }
    @Test fun fullPosterRowFitsEvenOnShortViewportAndLargeSizes() {
        for(side in listOf(false,true)) for(size in 0..4) {
            val columns=discoveryColumns(DiscoveryView.POSTERS,size,side)
            val width=if(side) 600f else 900f
            val top=if(side) 0f else discoveryTopBarHeight(width,360f,columns,DiscoveryView.POSTERS)
            assertTrue(top<=170f)
            val height=360f-top-10f
            val card=discoveryCardWidth(width,height,columns,DiscoveryView.POSTERS)
            assertTrue(card*1.5f<=height+.01f)
            assertTrue(card*columns+8f*(columns-1)<=width+.01f)
        }
    }

}
