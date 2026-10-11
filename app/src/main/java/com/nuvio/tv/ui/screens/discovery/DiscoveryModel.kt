package com.nuvio.tv.ui.screens.discovery

/** Shared policy for server queries and catalog enrichment. Null means unfiltered. */
data class DiscoveryFilters(
    val yearFrom: Int? = null, val yearTo: Int? = null,
    val genres: Set<Int> = emptySet(), val excludedGenres: Set<Int> = emptySet(),
    val scoreFrom: Double? = null, val scoreTo: Double? = null,
    val votes: Int? = DISCOVERY_MIN_VOTES, val runtimeFrom: Int? = null, val runtimeTo: Int? = null,
    val actors: List<DiscoveryChoice> = emptyList(), val allActors: Boolean = true,
    val company: DiscoveryChoice? = null, val country: String? = null,
    val language: String? = null, val keyword: DiscoveryChoice? = null,
    val certification: String? = null, val status: String? = null,
    val watched: String? = null, val releasedOnly: Boolean = true
) {
    val active: Boolean get() = copy(allActors = true, votes=null,releasedOnly=false) != DiscoveryFilters(votes=null,releasedOnly=false)
    fun query(movie: Boolean, today: java.time.LocalDate = java.time.LocalDate.now(), includeMissing: Boolean = false): Map<String, String> = buildMap {
        fun putValue(key: String, value: Any?) { if (value != null) put(key, value.toString()) }
        putValue(if (movie) "primary_release_date.gte" else "first_air_date.gte", yearFrom?.takeUnless {includeMissing}?.let { "$it-01-01" })
        putValue(if (movie) "primary_release_date.lte" else "first_air_date.lte", discoveryReleaseUpperBound(yearTo,releasedOnly,today))
        if (genres.isNotEmpty()) put("with_genres", genres.sorted().joinToString(","))
        if (excludedGenres.isNotEmpty()) put("without_genres", excludedGenres.sorted().joinToString(","))
        putValue("vote_average.gte", scoreFrom.takeUnless {includeMissing}); putValue("vote_average.lte", scoreTo.takeUnless {includeMissing})
        putValue("vote_count.gte", votes)
        putValue("with_runtime.gte", runtimeFrom.takeUnless {includeMissing}); putValue("with_runtime.lte", runtimeTo.takeUnless {includeMissing})
        if (movie && actors.isNotEmpty()) put("with_cast", actors.joinToString(if (allActors) "," else "|") { it.id })
        putValue("with_companies", company?.id); putValue("with_origin_country", country)
        putValue("with_original_language", language); putValue("with_keywords", keyword?.id)
        if (movie && certification != null && !includeMissing) { put("certification_country", "US"); put("certification", certification) }
        if (!movie) putValue("with_status", status)
    }
}
data class DiscoveryChoice(val id: String, val name: String)
enum class DiscoveryInfoPosition { TOP, SIDE }

fun discoveryInfoPosition(value: String?) = DiscoveryInfoPosition.entries.firstOrNull { it.name == value } ?: DiscoveryInfoPosition.TOP
fun DiscoveryFilters.cycleGenre(id: Int): DiscoveryFilters = when(id) {
    in genres -> copy(genres=genres-id,excludedGenres=excludedGenres+id)
    in excludedGenres -> copy(excludedGenres=excludedGenres-id)
    else -> copy(genres=genres+id,excludedGenres=excludedGenres-id)
}
fun discoveryUsesServerFilters(catalogId: String?, addonId: String?, includeMissing: Boolean): Boolean =
    !includeMissing && discoveryHasDiscoverEndpoint(catalogId,addonId)
enum class DiscoveryView { POSTERS, CLEAR_LOGO, LANDSCAPE, BANNERS;
    fun next() = entries[(ordinal + 1) % entries.size]
}

fun discoveryShouldPrefetch(focusedIndex: Int, count: Int, columns: Int): Boolean =
    focusedIndex >= 0 && count > 0 && focusedIndex >= (count - columns.coerceAtLeast(1) * 2).coerceAtLeast(0)
enum class DiscoverySort(val serverKey: String?) {
    POPULARITY("popularity"), TITLE(null), RELEASE(null), SCORE("vote_average"), VOTES("vote_count"), RUNTIME(null)
}
/** Curated exact keyword names. Resolve and validate IDs before exposing a choice. */
val discoveryTopics = listOf(
    DiscoveryChoice("time travel", "מסע בזמן"), DiscoveryChoice("superhero", "גיבורי־על"),
    DiscoveryChoice("based on novel or book", "מבוסס על ספר"), DiscoveryChoice("space travel", "מסע בחלל"),
    DiscoveryChoice("alien", "חייזרים")
)

/** Catalogs use both integer minutes and strings such as 1h 30m / PT1H30M. */
fun discoveryRuntimeMinutes(raw: String?): Int? {
    val value=raw?.trim()?.lowercase()?.takeIf{it.isNotEmpty()} ?: return null
    value.toIntOrNull()?.let{return it.takeIf{n->n>=0}}
    val hours=Regex("(\\d+)\\s*h").find(value)?.groupValues?.get(1)?.toIntOrNull()
    val minutes=Regex("(\\d+)\\s*m").find(value)?.groupValues?.get(1)?.toIntOrNull()
    if(hours!=null || minutes!=null) return (hours ?: 0)*60+(minutes ?: 0)
    return null
}

/** Legacy layouts become card styles inside the modern layout. */
fun discoveryCardStyle(value: String?): DiscoveryView = when (value) {
    "LIST" -> DiscoveryView.POSTERS
    "CARDS" -> DiscoveryView.LANDSCAPE
    else -> DiscoveryView.entries.firstOrNull { it.name == value } ?: DiscoveryView.POSTERS
}

fun discoveryColumns(style: DiscoveryView, size: Int, side: Boolean): Int {
    val sizes = when {
        style == DiscoveryView.POSTERS && side -> listOf(8, 7, 6, 5, 4)
        style == DiscoveryView.POSTERS -> listOf(10, 8, 7, 6, 5)
        side -> listOf(7, 6, 5, 4, 3)
        else -> listOf(8, 7, 6, 5, 4)
    }
    return sizes[size.coerceIn(0, 4)]
}

fun discoveryHeroImageUrl(url: String?): String? = url?.let {
    if(it.startsWith("https://image.tmdb.org/t/p/"))
        it.replace(Regex("/t/p/[^/]+/"),"/t/p/w1280/") else it
}

fun discoveryFallbackLogo(imdbId: String?): String? = imdbId?.takeIf {Regex("tt\\d+").matches(it)}
    ?.let {"https://images.metahub.space/logo/medium/$it/img"}

fun discoveryDecade(filters: DiscoveryFilters, decade: Int): DiscoveryFilters =
    filters.copy(yearFrom = decade, yearTo = decade + 9)

/** SVG artwork is served at its original size; Coil already has the native SVG decoder. */
fun discoveryLogoUrl(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let {
    if(it.startsWith("https://") || it.startsWith("http://")) it else "https://image.tmdb.org/t/p/original$it"
}

/** Product threshold: removes tiny voting samples without requiring blockbuster volume. */
const val DISCOVERY_MIN_VOTES = 100
fun discoveryDecades(year: Int = java.time.Year.now().value): List<Int> =
    (1950..(year / 10 * 10) step 10).reversed().toList()
fun discoveryParentDirection(rtl: Boolean): Int = if(rtl)
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT

/** Add one destination without changing existing memberships. */
fun discoveryAddedMembership(existing: Map<String, Boolean>, destination: String) = existing + (destination to true)
fun discoveryListSupports(tab: com.nuvio.tv.domain.model.LibraryListTab, type: String) =
    tab.isMembershipDestination && (tab.supportedContentTypes == null || type in tab.supportedContentTypes)

fun com.nuvio.tv.domain.model.MetaPreview.discoveryLibraryInput(): com.nuvio.tv.domain.model.LibraryEntryInput {
    val ids=com.nuvio.tv.data.repository.parseContentIds(id)
    return com.nuvio.tv.domain.model.LibraryEntryInput(itemId=id,itemType=apiType,title=name,
        year=releaseInfo?.take(4)?.toIntOrNull(),traktId=ids.trakt,simklId=ids.simkl,
        imdbId=imdbId ?: ids.imdb,tmdbId=ids.tmdb,poster=rawPosterUrl ?: poster,
        background=background,logo=logo,description=description,releaseInfo=releaseInfo,
        imdbRating=imdbRating,genres=genres,addonBaseUrl=sourceAddonBaseUrl)
}

fun discoveryVoteMatches(count: Int?, minimum: Int?) = minimum == null || count != null && count >= minimum

fun discoveryHasDiscoverEndpoint(catalogId:String?,addonId:String?) =
    catalogId == null || addonId == "nuvio.tmdb" && catalogId != "trending"
fun discoveryReleaseUpperBound(yearTo:Int?,releasedOnly:Boolean,today:java.time.LocalDate):String? {
    val selected=yearTo?.let {java.time.LocalDate.of(it,12,31)}
    return (if(releasedOnly) selected?.coerceAtMost(today) ?: today else selected)?.toString()
}
fun discoveryReleasedMatches(date:String?,enabled:Boolean,today:java.time.LocalDate=java.time.LocalDate.now()) =
    !enabled || date?.take(10)?.let {runCatching {java.time.LocalDate.parse(it)}.getOrNull()}?.let {it<=today} == true
fun DiscoveryFilters.needsDetails() = genres.isNotEmpty() || excludedGenres.isNotEmpty() || country!=null || language!=null ||
    company!=null || certification!=null || status!=null || actors.isNotEmpty() || keyword!=null || runtimeFrom!=null || runtimeTo!=null
fun discoveryCardRatio(style:DiscoveryView) = when(style) {DiscoveryView.POSTERS->2f/3f;DiscoveryView.CLEAR_LOGO->1f/.45f;DiscoveryView.BANNERS->3.2f;DiscoveryView.LANDSCAPE->16f/9f}
fun discoveryCardWidth(availableWidth:Float,availableHeight:Float,columns:Int,style:DiscoveryView):Float =
    minOf(((availableWidth-8f*(columns-1))/columns).coerceAtLeast(1f), (availableHeight*discoveryCardRatio(style)).coerceAtLeast(1f))
fun discoveryTopBarHeight(availableWidth:Float,availableHeight:Float,columns:Int,style:DiscoveryView):Float {
    val row=((availableWidth-8f*(columns-1))/columns)/discoveryCardRatio(style)
    return minOf(170f,(availableHeight-row-14f).coerceAtLeast(110f))
}

fun discoveryServerQuery(movie:Boolean,filters:DiscoveryFilters,sort:DiscoverySort,descending:Boolean,page:Int,
    language:String,includeMissing:Boolean,catalogId:String?=null,addonId:String?=null,
    today:java.time.LocalDate=java.time.LocalDate.now()):Map<String,String> = buildMap {
    if(discoveryHasDiscoverEndpoint(catalogId,addonId)) putAll(filters.query(movie,today,includeMissing))
    val key=sort.serverKey ?: if(sort==DiscoverySort.RELEASE) {if(movie) "primary_release_date" else "first_air_date"} else "popularity"
    put("language",language);put("page",page.toString());put("sort_by","$key.${if(descending) "desc" else "asc"}");put("include_adult","false")
    if(catalogId=="new") {
        put("sort_by",if(movie) "primary_release_date.desc" else "first_air_date.desc")
        val dateKey=if(movie) "primary_release_date.lte" else "first_air_date.lte"
        put(dateKey,minOf(this[dateKey] ?: today.toString(),today.toString()))
    }
}
