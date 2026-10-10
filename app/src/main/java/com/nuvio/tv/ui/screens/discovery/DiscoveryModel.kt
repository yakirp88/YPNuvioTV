package com.nuvio.tv.ui.screens.discovery

/** Shared policy for server queries and catalog enrichment. Null means unfiltered. */
data class DiscoveryFilters(
    val yearFrom: Int? = null, val yearTo: Int? = null,
    val genres: Set<Int> = emptySet(), val excludedGenres: Set<Int> = emptySet(),
    val scoreFrom: Double? = null, val scoreTo: Double? = null,
    val votes: Int? = null, val runtimeFrom: Int? = null, val runtimeTo: Int? = null,
    val actors: List<DiscoveryChoice> = emptyList(), val allActors: Boolean = true,
    val company: DiscoveryChoice? = null, val country: String? = null,
    val language: String? = null, val keyword: DiscoveryChoice? = null,
    val certification: String? = null, val status: String? = null,
    val watched: String? = null
) {
    val active: Boolean get() = copy(allActors = true) != DiscoveryFilters()
    fun query(movie: Boolean): Map<String, String> = buildMap {
        fun putValue(key: String, value: Any?) { if (value != null) put(key, value.toString()) }
        putValue(if (movie) "primary_release_date.gte" else "first_air_date.gte", yearFrom?.let { "$it-01-01" })
        putValue(if (movie) "primary_release_date.lte" else "first_air_date.lte", yearTo?.let { "$it-12-31" })
        if (genres.isNotEmpty()) put("with_genres", genres.sorted().joinToString(","))
        if (excludedGenres.isNotEmpty()) put("without_genres", excludedGenres.sorted().joinToString(","))
        putValue("vote_average.gte", scoreFrom); putValue("vote_average.lte", scoreTo)
        putValue("vote_count.gte", votes)
        putValue("with_runtime.gte", runtimeFrom); putValue("with_runtime.lte", runtimeTo)
        if (movie && actors.isNotEmpty()) put("with_cast", actors.joinToString(if (allActors) "," else "|") { it.id })
        putValue("with_companies", company?.id); putValue("with_origin_country", country)
        putValue("with_original_language", language); putValue("with_keywords", keyword?.id)
        if (movie && certification != null) { put("certification_country", "US"); put("certification", certification) }
        if (!movie) putValue("with_status", status)
    }
}
data class DiscoveryChoice(val id: String, val name: String)
enum class DiscoveryView { POSTERS, LIST, CARDS, CLEAR_LOGO, LANDSCAPE, BANNERS;
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
