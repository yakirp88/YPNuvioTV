package com.nuvio.tv.ui.screens.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.DiscoveryPreferences
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.data.remote.api.*
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.repository.*
import com.nuvio.tv.ui.screens.search.DiscoverCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class DiscoveryItem(val preview: MetaPreview, val names: List<String> = listOf(preview.name),
    val details: TmdbDetailsResponse? = null, val actors: Set<Int>? = null,
    val keywordIds: Set<Int>? = null, val popularity: Double? = null, val serverMatched: Boolean = false)
data class DiscoveryState(
    val movie: Boolean = true, val filters: DiscoveryFilters = DiscoveryFilters(),
    val query: String = "", val view: DiscoveryView = DiscoveryView.POSTERS, val size: Int = 1,
    val sort: DiscoverySort = DiscoverySort.POPULARITY, val descending: Boolean = true,
    val items: List<DiscoveryItem> = emptyList(), val visible: List<DiscoveryItem> = emptyList(),
    val catalogs: List<DiscoverCatalog> = builtinDiscoveryCatalogs(), val catalog: DiscoverCatalog? = null,
    val topics: List<DiscoveryChoice> = emptyList(),
    val genres: List<DiscoveryChoice> = emptyList(), val countries: List<DiscoveryChoice> = emptyList(),
    val languages: List<DiscoveryChoice> = emptyList(), val choices: List<DiscoveryChoice> = emptyList(),
    val metadataPending: Int = 0, val exporting: Boolean = false, val exportMessage: String? = null,
    val loading: Boolean = false, val hasMore: Boolean = true, val error: String? = null,
    val includeMissing: Boolean = false, val titleLanguages: List<String> = listOf("he", "en", "original"),
    val sourceLabel: String? = null,
    val localScope: Boolean = false, val page: Int = 0, val focusedId: String? = null
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val api: TmdbApi, private val tmdb: TmdbService,
    private val addons: AddonRepository, private val catalogs: CatalogRepository,
    private val collectionsStore: com.nuvio.tv.data.local.CollectionsDataStore,
    private val tmdbCollections: com.nuvio.tv.core.tmdb.TmdbCollectionSourceResolver,
    private val traktCollections: com.nuvio.tv.core.trakt.TraktPublicListSourceResolver,
    private val metadata: MetaRepository, val preferences: DiscoveryPreferences,
    private val progress: WatchProgressRepository, private val watchedSeries: WatchedSeriesStateHolder,
    private val library: LibraryRepository,
    val posterOptions: com.nuvio.tv.ui.components.posteroptions.PosterOptionsController
) : ViewModel() {
    private val mutable = MutableStateFlow(DiscoveryState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var suggestions: Job? = null
    private var exportJob: Job? = null
    private val enrichmentJobs = mutableListOf<Job>()
    private var configuration: Job? = null
    private var generation = 0
    private val pool = Semaphore(4)
    private val cache = object : LinkedHashMap<String, DiscoveryItem>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DiscoveryItem>?) = size > 500
    }
    private var watchedMovies = emptySet<String>()
    private var savedIds = emptySet<String>()
    private var collectionSources = emptyMap<String,CollectionSource>()
    private var addonCatalogs = emptyList<DiscoverCatalog>()
    private var skip = 0
    private var previous: DiscoveryState? = null
    private var previousRelated = false
    private var relatedSource = false
    private var retryRelated: (() -> Unit)? = null
    private var retryReset = false
    private fun apiKey(): String = prefsSnapshot?.get(stringPreferencesKey("tmdb_key"))?.takeIf { it.isNotBlank() } ?: tmdb.apiKey()
    private var activeProfile: Int? = null
    private var prefsReady = false
    private var prefsSnapshot: androidx.datastore.preferences.core.Preferences? = null

    init {
        posterOptions.bind(viewModelScope)
        viewModelScope.launch {
            preferences.profilePreferences.collect { (profile,p) ->
                if(activeProfile != profile) {
                    request?.cancel();exportJob?.cancel();generation++;previous=null;relatedSource=false
                    activeProfile=profile;prefsReady=false;mutable.value=DiscoveryState(catalogs=mutable.value.catalogs);synchronized(cache) { cache.clear() }
                }
                val s = mutable.value
                val langs = listOf("primary", "secondary", "tertiary").mapIndexed { i, k -> p[stringPreferencesKey(k)] ?: listOf("he", "en", "original")[i] }
                val missing = p[androidx.datastore.preferences.core.booleanPreferencesKey("include_missing")] ?: false
                val reload = !prefsReady || s.titleLanguages != langs || s.includeMissing != missing || prefsSnapshot?.get(stringPreferencesKey("tmdb_key")) != p[stringPreferencesKey("tmdb_key")]
                prefsSnapshot = p
                mutable.update { it.copy(includeMissing = missing, titleLanguages = langs) }
                if (!prefsReady) { restoreDisplay(); prefsReady = true }
                if (reload) { refreshConfiguration();load(reset = true) }
            }
        }
        viewModelScope.launch { progress.observeWatchedMovieIds().collect { watchedMovies = it; render() } }
        viewModelScope.launch { watchedSeries.fullyWatchedSeriesIds.collect { render() } }
        viewModelScope.launch { library.libraryItems.collect { entries -> savedIds = entries.map { it.id }.toSet(); render() } }
        viewModelScope.launch {
            combine(addons.getInstalledAddons(),collectionsStore.collections) { installed, saved -> installed to saved }.collect { (installed,saved) ->
                val choices = installed.enabledAddons().flatMap { addon -> addon.catalogs.filter { c -> c.extra.none { it.isRequired } }.map { c ->
                    DiscoverCatalog("${addon.baseUrl}:${c.apiType}:${c.id}", addon.id, addon.displayName, addon.baseUrl,
                        c.id, c.name, c.apiType, emptyList(), c.supportsExtra("skip"), c.skipStep())
                } }
                addonCatalogs=choices
                val sources=linkedMapOf<String,CollectionSource>()
                val native=saved.flatMap{ collection -> collection.folders.flatMap{ folder -> folder.sources.mapIndexedNotNull{index,source ->
                    val type=when(source){is TmdbCollectionSource -> if(source.mediaType==TmdbCollectionMediaType.MOVIE) "movie" else "series";is TraktCollectionSource -> if(source.mediaType==TmdbCollectionMediaType.MOVIE) "movie" else "series";is AddonCatalogCollectionSource -> source.type}
                    val key="native:${collection.id}:${folder.id}:$index"
                    sources[key]=source
                    DiscoverCatalog(key,"nuvio.collection","נוביו · ${collection.title}","",key,folder.title,type,emptyList(),true,20)
                } } }
                collectionSources=sources
                mutable.update { it.copy(catalogs = builtinDiscoveryCatalogs()+native+choices) }
            }
        }
    }
    private fun refreshConfiguration() {
        configuration?.cancel()
        if(apiKey().isBlank()) return
        configuration = viewModelScope.launch {
            try {
                val genres = (if(mutable.value.movie) api.getMovieGenres(apiKey(),"he") else api.getTvGenres(apiKey(),"he")).body()?.genres.orEmpty().map { DiscoveryChoice(it.id.toString(),it.name) }
                mutable.update { it.copy(genres=genres) }
                val countries = api.discoveryCountries(apiKey()).body().orEmpty().map { DiscoveryChoice(it.code, it.name?.ifBlank { null } ?: it.englishName) }
                val languages = api.discoveryLanguages(apiKey()).body().orEmpty().map { DiscoveryChoice(it.code, it.name.ifBlank { it.englishName }) }
                val topics=coroutineScope { discoveryTopics.map { seed -> async {
                    api.searchKeywords(apiKey(),seed.id).body()?.results?.firstOrNull { it.name.equals(seed.id,true) }?.let{DiscoveryChoice(it.id.toString(),seed.name)}
                } }.awaitAll().filterNotNull() }
                mutable.update { it.copy(countries = countries.sortedBy { c -> c.name }, languages = languages.sortedBy { l -> l.name },topics=topics) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Retry via lookup, no fabricated countries. */ }
        }
    }
    private fun restoreDisplay() {
        val p = prefsSnapshot ?: return
        val kind = if (mutable.value.movie) "movie" else "series"
        val view = runCatching { DiscoveryView.valueOf(p[stringPreferencesKey("${kind}_view")] ?: "POSTERS") }.getOrDefault(DiscoveryView.POSTERS)
        val sort = runCatching { DiscoverySort.valueOf(p[stringPreferencesKey("${kind}_sort")] ?: "POPULARITY") }.getOrDefault(DiscoverySort.POPULARITY)
        val size = p[stringPreferencesKey("${kind}_${view.name}_size")]?.toIntOrNull()?.coerceIn(0, 2) ?: 1
        val desc = p[stringPreferencesKey("${kind}_descending")] != "false"
        mutable.update { it.copy(view = view, sort = sort, size = size, descending = desc) }
    }
    fun cycleView() {
        val s = mutable.value; val v = s.view.next(); val kind = if (s.movie) "movie" else "series"
        val size = prefsSnapshot?.get(stringPreferencesKey("${kind}_${v.name}_size"))?.toIntOrNull()?.coerceIn(0,2) ?: 1
        mutable.update { it.copy(view = v, size = size) }
        viewModelScope.launch { preferences.save("${kind}_view", v.name) }
    }
    fun cycleSize() {
        val s = mutable.value; val size = (s.size + 1) % 3
        mutable.update { it.copy(size = size) }
        viewModelScope.launch { preferences.save("${if(s.movie) "movie" else "series"}_${s.view.name}_size", size.toString()) }
    }
    fun selectType(movie: Boolean) {
        if (movie == mutable.value.movie) return
        mutable.update { it.copy(movie = movie, catalog = null, filters = DiscoveryFilters(), query = "", focusedId = null, sourceLabel=null, items = emptyList(), visible = emptyList()) }
        relatedSource=false;restoreDisplay(); refreshConfiguration(); load(true)
    }
    fun selectCatalog(catalog: DiscoverCatalog?) {
        relatedSource=false
        mutable.update { it.copy(catalog = catalog, filters = DiscoveryFilters(), query = "", focusedId = null, sourceLabel=null, items = emptyList(), visible = emptyList()) }; load(true)
    }
    fun reset() { relatedSource=false;previous=null;mutable.update { it.copy(catalog = null, filters = DiscoveryFilters(), query = "", focusedId = null, sourceLabel=null, items = emptyList(), visible = emptyList()) }; load(true) }
    fun text(value: String) { mutable.update { it.copy(query = value) }; render() }
    fun focus(id: String) { mutable.update { it.copy(focusedId = id) } }
    fun filter(f: DiscoveryFilters) {
        if (f.yearFrom != null && f.yearTo != null && f.yearFrom > f.yearTo ||
            f.scoreFrom != null && f.scoreTo != null && f.scoreFrom > f.scoreTo ||
            f.runtimeFrom != null && f.runtimeTo != null && f.runtimeFrom > f.runtimeTo) {
            mutable.update { it.copy(error = "תחילת הטווח חייבת להיות קטנה מסופו") }; return
        }
        mutable.update { it.copy(filters = f) }; render(); if(!relatedSource) load(true, debounce = true)
    }
    fun sort(sort: DiscoverySort = mutable.value.sort, descending: Boolean = mutable.value.descending) {
        val kind = if(mutable.value.movie) "movie" else "series"
        mutable.update { it.copy(sort = sort, descending = descending,localScope=relatedSource || it.catalog!=null || it.includeMissing || it.filters.watched!=null || sort==DiscoverySort.TITLE || sort==DiscoverySort.RUNTIME) }
        viewModelScope.launch { preferences.save("${kind}_sort", sort.name); preferences.save("${kind}_descending", descending.toString()) }
        if(relatedSource) render() else load(true)
    }
    fun lookup(kind: String, query: String) {
        suggestions?.cancel(); mutable.update { it.copy(choices = emptyList()) }
        if(query.isBlank()) return
        suggestions = viewModelScope.launch {
            delay(250)
            try {
                val s = mutable.value
                val found = when(kind) {
                    "actor" -> api.searchPeople(apiKey(), query, "he").body()?.results.orEmpty().filter { it.knownForDepartment == "Acting" }.map { DiscoveryChoice(it.id.toString(), it.name.orEmpty()) }
                    "company" -> api.searchCompanies(apiKey(), query).body()?.results.orEmpty().map { DiscoveryChoice(it.id.toString(), it.name.orEmpty()) }
                    "country" -> s.countries.filter { it.name.contains(query,true) || it.id.contains(query,true) }
                    else -> s.languages.filter { it.name.contains(query,true) || it.id.contains(query,true) }
                }
                mutable.update { it.copy(choices = found.take(12)) }
            } catch(e: CancellationException) { throw e } catch(_: Exception) { mutable.update { it.copy(error = "לא ניתן לטעון הצעות. נסה שוב") } }
        }
    }
    fun restoreSource() { previous?.let { request?.cancel(); generation++; relatedSource=previousRelated; mutable.value=it; previous=null } }
    fun actorFromTitle(p: MetaPreview) {
        suggestions?.cancel(); mutable.update { it.copy(choices=emptyList()) }
        suggestions=viewModelScope.launch {
            try {
                val id=p.id.removePrefix("tmdb:").toIntOrNull() ?: api.findByExternalId(p.imdbId ?: p.id,apiKey()).body()?.movieResults?.firstOrNull()?.id ?: return@launch
                val result=if(mutable.value.movie) api.getMovieCredits(id,apiKey()) else api.getTvCredits(id,apiKey())
                val people=result.body()?.cast.orEmpty().take(15).mapNotNull { c -> c.id?.let { DiscoveryChoice(it.toString(),c.name.orEmpty()) } } +
                    result.body()?.crew.orEmpty().filter { it.job == "Director" }.mapNotNull { c -> c.id?.let { DiscoveryChoice("director:$it","${c.name} · במאי") } }
                mutable.update { it.copy(choices=people) }
            } catch(e:CancellationException){throw e}catch(_:Exception){mutable.update{it.copy(error="לא ניתן לטעון שחקנים")}}
        }
    }
    fun chooseTitleActor(person: DiscoveryChoice) {
        if(person.id.startsWith("director:")) { director(person); return }
        previousRelated=relatedSource;relatedSource=false;previous=mutable.value
        mutable.update { it.copy(catalog=null,filters=DiscoveryFilters(actors=listOf(person)),query="",sourceLabel=person.name) }
        load(true)
    }
    private fun director(person:DiscoveryChoice) {
        previousRelated = relatedSource
        relatedSource=true
        request?.cancel();generation++;previous=mutable.value
        val origin = previous
        val originRelated = previousRelated
        retryRelated = { director(person); previous = origin; previousRelated=originRelated }
        mutable.update{it.copy(loading=true,error=null,catalog=null,sourceLabel=person.name,items=emptyList(),visible=emptyList(),filters=DiscoveryFilters(),query="")}
        request=viewModelScope.launch {
            try {
                val s=mutable.value
                val result=api.getPersonCombinedCredits(person.id.removePrefix("director:").toInt(),apiKey(),s.titleLanguages.firstOrNull{it!="original"})
                if(!result.isSuccessful) error("לא ניתן לטעון תוכן של הבמאי")
                val raw=result.body()?.crew.orEmpty().filter{it.job=="Director" && it.mediaType==(if(s.movie) "movie" else "tv")}.map{r->
                    MetaPreview("tmdb:${r.id}",if(s.movie) ContentType.MOVIE else ContentType.SERIES,name=r.title ?: r.name.orEmpty(),
                        poster=image(r.posterPath),posterShape=PosterShape.POSTER,background=image(r.backdropPath),logo=null,description=r.overview,
                        releaseInfo=(r.releaseDate ?: r.firstAirDate)?.take(4),imdbRating=r.voteAverage?.toFloat(),genres=emptyList(),released=r.releaseDate ?: r.firstAirDate,voteCount=r.voteCount)
                }
                val data=coroutineScope{raw.map{async{pool.withPermit{enrich(it,s)}}}.awaitAll()}
                mutable.update{it.copy(items=data,loading=false,hasMore=false,localScope=true)};render()
            }catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(loading=false,error=e.message)}}
        }
    }
    fun similar(p:MetaPreview) {
        previousRelated = relatedSource
        relatedSource=true
        request?.cancel(); generation++; previous=mutable.value
        val origin = previous
        val originRelated = previousRelated
        retryRelated = { similar(p); previous = origin; previousRelated=originRelated }
        mutable.update { it.copy(loading=true,error=null,catalog=null,sourceLabel="תוכן דומה · ${p.name}",items=emptyList(),visible=emptyList(),query="",filters=DiscoveryFilters()) }
        request=viewModelScope.launch {
            try {
                val s=mutable.value
                val id=p.id.removePrefix("tmdb:").toIntOrNull() ?: api.findByExternalId(p.imdbId ?: p.id,apiKey()).body()?.let{if(s.movie) it.movieResults?.firstOrNull()?.id else it.tvResults?.firstOrNull()?.id} ?: error("לא נמצא מזהה TMDB לכותר")
                val result=if(s.movie) api.getMovieRecommendations(id,apiKey(),s.titleLanguages.firstOrNull { it!="original" }) else api.getTvRecommendations(id,apiKey(),s.titleLanguages.firstOrNull { it!="original" })
                if(!result.isSuccessful) error("לא ניתן לטעון תוכן דומה")
                val raw=result.body()?.results.orEmpty().map { r -> MetaPreview("tmdb:${r.id}",if(s.movie) ContentType.MOVIE else ContentType.SERIES,
                    name=r.title ?: r.name.orEmpty(),poster=image(r.posterPath),posterShape=PosterShape.POSTER,background=image(r.backdropPath),logo=null,
                    description=r.overview,releaseInfo=(r.releaseDate ?: r.firstAirDate)?.take(4),imdbRating=r.voteAverage?.toFloat(),genres=emptyList(),released=r.releaseDate ?: r.firstAirDate,voteCount=r.voteCount) }
                val data=coroutineScope {raw.map{async{pool.withPermit{enrich(it,s)}}}.awaitAll()}
                mutable.update{it.copy(items=data,loading=false,hasMore=false,localScope=true)};render()
            }catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(loading=false,error=e.message)}}
        }
    }
    fun retry() {
        if(relatedSource) retryRelated?.invoke() else load(reset=retryReset || mutable.value.items.isEmpty())
    }
    fun load(reset: Boolean = false, debounce: Boolean = false) {
        val current = mutable.value
        if(!reset && (current.loading || !current.hasMore)) return
        request?.cancel()
        if(reset) { generation++; enrichmentJobs.forEach { it.cancel() }; enrichmentJobs.clear() }
        val token = generation
        mutable.update { it.copy(loading = true, error = null, items = it.items, visible = it.visible) }
        request = viewModelScope.launch {
            if(debounce) delay(180)
            val s = mutable.value
            try {
                if (s.catalog == null && apiKey().isBlank()) error("יש להגדיר מפתח TMDB בהגדרות → אינטגרציות → TMDB")
                val result=fetchPage(s,if(reset) 1 else s.page+1,skip,reset)
                val raw=result.raw; val popularities=result.popularities; val pendingSkip=result.skip
                val more=result.more; val next=result.page
                if(token != generation) return@launch
                skip = pendingSkip
                retryReset = false
                val serverMatched = s.catalog == null && !s.includeMissing
                val initial = raw.map { p -> DiscoveryItem(p, popularity=popularities[p.id], serverMatched=serverMatched) }
                mutable.update { it.copy(items=(if(reset) initial else it.items+initial).distinctBy { item -> item.preview.id },
                    loading=false,metadataPending=(if(reset) 0 else it.metadataPending)+raw.size,hasMore=more,page=next,
                    localScope=s.catalog != null || s.includeMissing || s.filters.watched != null || s.sort == DiscoverySort.TITLE || s.sort == DiscoverySort.RUNTIME) }
                render()
                // Artwork and translations must not block page navigation or prefetch.
                enrichmentJobs.removeAll { it.isCompleted }
                enrichmentJobs += viewModelScope.launch {
                    val enriched = coroutineScope { raw.map { p -> async { pool.withPermit {
                        enrich(p,s).copy(popularity=popularities[p.id],serverMatched=serverMatched)
                    } } }.awaitAll() }.associateBy { it.preview.id }
                    if(token == generation) {
                        mutable.update { it.copy(metadataPending=(it.metadataPending-raw.size).coerceAtLeast(0),items=it.items.map { item -> enriched[item.preview.id] ?: item }) }; render()
                    }
                }
            } catch(e: CancellationException) { throw e } catch(e: Exception) {
                if(token == generation) {
                    retryReset = reset
                    mutable.update { it.copy(loading=false,error=e.message ?: "הטעינה נכשלה") }
                }
            }
        }
    }
    private data class DiscoveryPage(val raw:List<MetaPreview>,val popularities:Map<String,Double?>,val more:Boolean,val skip:Int,val page:Int,val limited:Boolean=false)
    private suspend fun fetchPage(s:DiscoveryState,page:Int,offsetBase:Int,reset:Boolean):DiscoveryPage {
                val raw: List<MetaPreview>
                var popularities = emptyMap<String, Double?>()
                var limited = false
                var pendingSkip = offsetBase
                val next = page
                val more: Boolean
                if(s.catalog != null && s.catalog.addonId != "nuvio.tmdb") {
                    val c = s.catalog
                    val offset = if(reset) 0 else offsetBase
                    val source=collectionSources[c.key]
                    val stream=when(source) {
                        is TmdbCollectionSource -> tmdbCollections.resolve(source,next)
                        is TraktCollectionSource -> traktCollections.resolve(source,next)
                        is AddonCatalogCollectionSource -> {
                            val target=addonCatalogs.firstOrNull{it.addonId==source.addonId && it.catalogId==source.catalogId && it.type==source.type} ?: error("תוסף הקטלוג אינו מחובר")
                            catalogs.getCatalog(target.addonBaseUrl,target.addonId,target.addonName,target.catalogId,target.catalogName,target.type,skip=offset,
                                skipStep=target.skipStep,supportsSkip=target.supportsSkip,extraArgs=source.genre?.let{mapOf("genre" to it)} ?: emptyMap())
                        }
                        else -> catalogs.getCatalog(c.addonBaseUrl,c.addonId,c.addonName,c.catalogId,c.catalogName,c.type,
                            skip=offset,skipStep=c.skipStep,supportsSkip=c.supportsSkip)
                    }
                    val result=stream.first { it !is NetworkResult.Loading }
                    if(result is NetworkResult.Error) error(result.message)
                    val row = (result as NetworkResult.Success).data
                    raw = row.items; more = row.hasMore && c.supportsSkip
                    pendingSkip = offset + raw.size.coerceAtLeast(c.skipStep)
                } else {
                    val key = s.sort.serverKey ?: if(s.sort == DiscoverySort.RELEASE) { if(s.movie) "primary_release_date" else "first_air_date" } else "popularity"
                    val serverQuery=buildMap<String,String> {
                        if(!s.includeMissing && s.catalog == null) putAll(s.filters.query(s.movie))
                        put("language", s.titleLanguages.firstOrNull { it != "original" } ?: "en")
                        put("page",next.toString()); put("sort_by", "$key.${if(s.descending) "desc" else "asc"}")
                        put("include_adult","false")
                        if(s.catalog?.catalogId=="new") {
                            put("sort_by",if(s.movie) "primary_release_date.desc" else "first_air_date.desc")
                            put(if(s.movie) "primary_release_date.lte" else "first_air_date.lte",java.time.LocalDate.now().toString())
                        }
                    }
                    val response=if(s.catalog?.catalogId=="trending") api.discoveryTrending(if(s.movie) "movie" else "tv",apiKey(),next,serverQuery["language"])
                        else api.discoverContent(if(s.movie) "movie" else "tv",apiKey(),serverQuery)
                    if(!response.isSuccessful) error("TMDB: ${response.code()}")
                    val data = response.body() ?: error("תגובה ריקה")
                    limited = (data.totalPages ?: 1)>500
                    more = next < (data.totalPages ?: 1).coerceAtMost(500)
                    raw = data.results.orEmpty().map { r -> MetaPreview("tmdb:${r.id}", if(s.movie) ContentType.MOVIE else ContentType.SERIES,
                        name = r.title ?: r.name ?: r.originalTitle ?: r.originalName ?: "", poster = image(r.posterPath),posterShape=PosterShape.POSTER,
                        background=image(r.backdropPath),logo=null,description=r.overview,releaseInfo=(r.releaseDate ?: r.firstAirDate)?.take(4),
                        imdbRating=r.voteAverage?.toFloat(),genres=emptyList(),voteCount=r.voteCount,released=r.releaseDate ?: r.firstAirDate) }
                    popularities = data.results.orEmpty().associate { "tmdb:${it.id}" to it.popularity }
                }
        return DiscoveryPage(raw,popularities,more,pendingSkip,next,limited)
    }
    fun cancelExport() { exportJob?.cancel(); mutable.update { it.copy(exporting=false,exportMessage="הייצוא בוטל; לא נשמר קטלוג חלקי") } }
    fun exportCatalog(name:String) {
        if(name.isBlank() || mutable.value.exporting) return
        val snapshot=mutable.value;val profile=activeProfile ?: return
        val related=relatedSource
        mutable.update { it.copy(exporting=true,exportMessage="אוסף תוצאות…") }
        exportJob=viewModelScope.launch {
            try {
                val saved=withContext(Dispatchers.IO) {
                    val output=linkedMapOf<String,MetaPreview>()
                    var page=1;var offset=0
                    do {
                        ensureActive()
                        val data=if(related) DiscoveryPage(snapshot.items.map { it.preview },emptyMap(),false,0,1)
                            else fetchPage(snapshot,page,offset,page==1)
                        if(data.limited) error("יותר מ־500 עמודים: יש לצמצם את הסינון כדי לשמור קטלוג מלא")
                        val trusted=snapshot.catalog==null && !snapshot.includeMissing && !related
                        val items=if(related) snapshot.items else if(trusted && snapshot.query.isBlank() && snapshot.filters.watched==null)
                            data.raw.map { DiscoveryItem(it,serverMatched=true) }
                        else coroutineScope { data.raw.map { p -> async { pool.withPermit { enrich(p,snapshot,strict=true).copy(serverMatched=trusted) } } }.awaitAll() }
                        filteredState(snapshot.copy(items=items)).visible.forEach { output[it.preview.id]=it.preview }
                        withContext(Dispatchers.Main) { mutable.update { it.copy(exportMessage="עמוד $page · ${output.size} כותרים") } }
                        if(!data.more) break
                        if(page>=500) error("המקור מגביל את החיפוש ל־500 עמודים. צמצם את הסינון; לא נשמר קטלוג חלקי")
                        page++;offset=data.skip
                    } while(true)
                    output.values.map { SavedDiscoveryItem.from(it) }
                }
                ensureActive()
                if(activeProfile!=profile) error("הפרופיל השתנה; הייצוא בוטל")
                val source=TmdbCollectionSource(TmdbCollectionSourceType.DISCOVER,name.trim(),mediaType=if(snapshot.movie) TmdbCollectionMediaType.MOVIE else TmdbCollectionMediaType.TV,snapshot=saved)
                val collection=com.nuvio.tv.domain.model.Collection(collectionsStore.generateId(),name.trim(),pinToTop=true,
                    folders=listOf(CollectionFolder(collectionsStore.generateId(),name.trim(),coverImageUrl=saved.firstOrNull()?.poster,sources=listOf(source))))
                withContext(Dispatchers.IO) { collectionsStore.addCollection(collection,profile) }
                mutable.update { it.copy(exporting=false,exportMessage="נשמר קטלוג: ${name.trim()} · ${saved.size} כותרים") }
            } catch(e:CancellationException) { throw e } catch(e:Exception) {
                mutable.update { it.copy(exporting=false,exportMessage=e.message ?: "הייצוא נכשל; לא נשמר קטלוג חלקי") }
            }
        }
    }
    private fun image(path: String?) = path?.let { "https://image.tmdb.org/t/p/w500$it" }
    private suspend fun enrich(p: MetaPreview, s: DiscoveryState, strict:Boolean=false): DiscoveryItem {
        val key = "${p.apiType}:${p.id}:${s.titleLanguages.joinToString()}:${s.filters.actors.isNotEmpty()}:${s.filters.keyword != null}:${s.filters.certification != null}"
        synchronized(cache) { cache[key] }?.let { return it }
        try {
            val id = p.id.removePrefix("tmdb:").toIntOrNull() ?: api.findByExternalId(p.imdbId ?: p.id,apiKey()).body()?.let { if(s.movie) it.movieResults?.firstOrNull()?.id else it.tvResults?.firstOrNull()?.id }
                ?: return DiscoveryItem(p)
            val append = buildList { add("translations"); add("images"); add("external_ids")
                if(s.filters.certification != null) add("release_dates")
                if(s.filters.actors.isNotEmpty() && s.movie) add("credits")
                if(s.filters.keyword != null) add("keywords") }.joinToString(",")
            val details = api.discoveryDetails(if(s.movie) "movie" else "tv",id,apiKey(),s.titleLanguages.firstOrNull { it != "original" },append,
                (s.titleLanguages.filter { it != "original" } + listOf("null")).distinct().joinToString(","))
            val d = details.body() ?: if(strict) error("טעינת המטא־דאטה נכשלה; הייצוא לא הושלם") else return DiscoveryItem(p)
            val original = d.originalTitle ?: d.originalName ?: p.name
            val translations = d.translations?.translations.orEmpty()
            val names = s.titleLanguages.map { lang ->
                if(lang == "original") original else translations.firstOrNull { it.language == lang && !(it.data.title ?: it.data.name).isNullOrBlank() }?.data?.let { it.title ?: it.name }.orEmpty()
            }
            val searchNames = translations.filter { it.language in listOf("he", "en") }.mapNotNull { it.data.title ?: it.data.name }
            val logoLanguages = (s.titleLanguages.map { if(it == "original") d.originalLanguage else it }.filterNotNull().filter(String::isNotBlank) + "null").distinct().joinToString(",")
            val imageData = d.images
            val age = if(s.movie) d.releaseDates?.results?.firstOrNull { it.iso31661 == "US" }?.releaseDates?.firstNotNullOfOrNull { it.certification?.takeIf(String::isNotBlank) } else null
            val actors = if(s.filters.actors.isNotEmpty() && s.movie) d.credits?.cast?.mapNotNull { it.id }?.toSet() else null
            val keywords = if(s.filters.keyword != null) d.keywords?.let { it.keywords ?: it.results }?.map { it.id }?.toSet() else null
            val imdb = p.imdbId ?: d.externalIds?.imdbId
            val item = DiscoveryItem(p.copy(imdbId=imdb,name=names.firstOrNull(String::isNotBlank) ?: original,
                poster=image(d.posterPath) ?: p.poster,background=image(d.backdropPath) ?: p.background,
                logo=imageData?.logos?.let { logos -> (s.titleLanguages.map { if(it=="original") d.originalLanguage else it } + listOf(null)).firstNotNullOfOrNull { lang -> logos.firstOrNull { it.iso6391==lang }?.filePath } }?.let { "https://image.tmdb.org/t/p/w500$it" } ?: p.logo,
                imdbRating=d.voteAverage?.toFloat() ?: p.imdbRating,description=d.overview ?: p.description,genres=d.genres.orEmpty().map { it.name },runtime=(d.runtime ?: d.episodeRunTime?.firstOrNull())?.toString(),
                released=d.releaseDate ?: d.firstAirDate ?: p.released,releaseInfo=(d.releaseDate ?: d.firstAirDate)?.take(4) ?: p.releaseInfo,language=d.originalLanguage,status=d.status,ageRating=age,country=d.originCountry?.joinToString() ?: d.productionCountries?.mapNotNull { it.iso31661 }?.joinToString(),voteCount=d.voteCount ?: p.voteCount),
                (names + searchNames + original + p.name).filter(String::isNotBlank).distinct(),
                d.copy(images=null,translations=null,externalIds=null,releaseDates=null,credits=null,keywords=null),actors,keywords,d.popularity)
            synchronized(cache) { cache[key] = item }
            return item
        } catch(e: CancellationException) { throw e } catch(e: Exception) { if(strict) throw e; return DiscoveryItem(p) }
    }
    private fun render() { mutable.update(::filteredState) }
    private fun filteredState(s:DiscoveryState):DiscoveryState {
            fun accepts(known: Boolean, matches: () -> Boolean) = if(!known) s.includeMissing else matches()
            val watchedIds = if(s.movie) watchedMovies else watchedSeries.fullyWatchedSeriesIds.value
            fun member(set: Set<String>, p: MetaPreview) = p.id in set || p.imdbId?.let { it in set } == true
            val filtered = s.items.filter { item ->
                // TMDB has already applied these criteria. Do not hide valid raw results
                // while waiting for decorative metadata. Personal membership stays local.
                val f = if(item.serverMatched) DiscoveryFilters(watched=s.filters.watched) else s.filters
                val p = item.preview; val d = item.details
                val year = p.releaseInfo?.take(4)?.toIntOrNull(); val duration = discoveryRuntimeMinutes(p.runtime)
                (s.query.isBlank() || item.names.any { it.contains(s.query.trim(),true) }) &&
                ((f.yearFrom == null && f.yearTo == null) || accepts(year != null) { year!! >= (f.yearFrom ?: 0) && year <= (f.yearTo ?: 9999) }) &&
                ((f.scoreFrom == null && f.scoreTo == null) || accepts(p.imdbRating != null) { p.imdbRating!! >= (f.scoreFrom ?: 0.0) && p.imdbRating <= (f.scoreTo ?: 10.0) }) &&
                (f.votes == null || accepts(p.voteCount != null) { p.voteCount!! >= f.votes }) &&
                ((f.runtimeFrom == null && f.runtimeTo == null) || accepts(duration != null) { duration!! >= (f.runtimeFrom ?: 0) && duration <= (f.runtimeTo ?: Int.MAX_VALUE) }) &&
                ((f.genres.isEmpty() && f.excludedGenres.isEmpty()) || accepts(d?.genres != null) { val ids=d!!.genres!!.map { it.id }; f.genres.all { it in ids } && f.excludedGenres.none { it in ids } }) &&
                (f.country == null || accepts(p.country != null) { p.country!!.split(',').any { it.trim() == f.country } }) &&
                (f.language == null || accepts(p.language != null) { p.language == f.language }) &&
                (f.company == null || accepts(d?.productionCompanies != null) { d!!.productionCompanies!!.any { it.id?.toString() == f.company.id } }) &&
                (f.certification == null || accepts(p.ageRating != null) { p.ageRating == f.certification }) &&
                (f.status == null || accepts(p.status != null) { val expected=mapOf("0" to "Returning Series","3" to "Ended","4" to "Canceled")[f.status]; p.status == expected }) &&
                (f.actors.isEmpty() || accepts(item.actors != null) { if(f.allActors) f.actors.all { it.id.toIntOrNull() in item.actors!! } else f.actors.any { it.id.toIntOrNull() in item.actors!! } }) &&
                (f.keyword == null || accepts(item.keywordIds != null) { f.keyword.id.toIntOrNull() in item.keywordIds!! }) &&
                (f.watched == null || when(f.watched) { "watched" -> member(watchedIds,p); "unwatched" -> !member(watchedIds,p); else -> member(savedIds,p) })
            }
            val sorted = when(s.sort) {
                DiscoverySort.TITLE -> filtered.sortedBy { it.preview.name.lowercase() }
                DiscoverySort.RELEASE -> filtered.sortedBy { it.preview.released ?: it.preview.releaseInfo ?: "" }
                DiscoverySort.SCORE -> filtered.sortedBy { it.preview.imdbRating ?: -1f }
                DiscoverySort.VOTES -> filtered.sortedBy { it.preview.voteCount ?: -1 }
                DiscoverySort.RUNTIME -> filtered.sortedBy { discoveryRuntimeMinutes(it.preview.runtime) ?: -1 }
                DiscoverySort.POPULARITY -> if(s.catalog == null && !relatedSource) filtered else filtered.sortedBy { it.popularity ?: -1.0 }
            }
            val order = if(s.descending && (s.sort != DiscoverySort.POPULARITY || s.catalog != null || relatedSource)) sorted.reversed() else sorted
            return s.copy(visible=order)
    }
}

private fun builtinDiscoveryCatalogs():List<DiscoverCatalog> = listOf("movie","series").flatMap { type ->
    listOf("popular" to "פופולרי","trending" to "טרנדי","new" to "חדש").map { (id,name) ->
        DiscoverCatalog("tmdb:$type:$id","nuvio.tmdb","נוביו","",id,name,type,emptyList(),true,20)
    }
}
