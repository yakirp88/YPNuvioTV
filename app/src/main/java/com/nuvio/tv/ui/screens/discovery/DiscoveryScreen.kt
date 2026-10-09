@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val accent = Color(0xff58cccc)
private val background = Color(0xff101418)
private val panel = Color(0xff1b232a)
private fun DiscoveryView.label() = when(this) {
    DiscoveryView.POSTERS -> "רשת פוסטרים"; DiscoveryView.LIST -> "רשימה"; DiscoveryView.CARDS -> "כרטיסים גדולים"
    DiscoveryView.CLEAR_LOGO -> "Clear Logo"; DiscoveryView.LANDSCAPE -> "פוסטרים רחבים"; DiscoveryView.BANNERS -> "באנרים"
}
private fun DiscoverySort.label() = when(this) {
    DiscoverySort.POPULARITY -> "פופולריות"; DiscoverySort.TITLE -> "שם הכותר"; DiscoverySort.RELEASE -> "תאריך יציאה"
    DiscoverySort.SCORE -> "ציון"; DiscoverySort.VOTES -> "כמות הצבעות"; DiscoverySort.RUNTIME -> "משך"
}

@Composable
fun ContentDiscoveryScreen(onNavigateToDetail: (String, String, String) -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsState()
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var showText by rememberSaveable { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<MetaPreview?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val firstToolbar = remember { FocusRequester() }
    val textFocus = remember { FocusRequester() }
    var returning by rememberSaveable { mutableStateOf(false) }
    val resumeFromDetail = remember { returning }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME && returning) {
            returning = false
            s.focusedId?.let { requesters[it]?.let { r -> runCatching { r.requestFocus() } } }
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if(!resumeFromDetail) {
            if(!s.movie) viewModel.selectType(true)
            else if(s.filters.active || s.query.isNotBlank() || s.catalog != null || s.sourceLabel != null) viewModel.reset()
        }
        val id = s.focusedId.takeIf { resumeFromDetail }
        val index = s.visible.indexOfFirst { it.preview.id == id }
        if(index >= 0) {
            grid.scrollToItem(index)
            withFrameNanos { }
            withFrameNanos { }
        }
        val r=id?.let{requesters[it]}
        if(r != null) runCatching{r.requestFocus()} else firstToolbar.requestFocus()
        returning=false
    }
    LaunchedEffect(showText) { if(showText) textFocus.requestFocus() }
    LaunchedEffect(hint) { if(hint != null) { delay(1800); hint = null } }
    // Deliberately trigger only when the user has reached the end, never drain a catalog
    // automatically merely because a local text filter has zero matches.
    LaunchedEffect(grid, s.visible.size, s.hasMore, s.loading,s.error) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.distinctUntilChanged().collect { last ->
            if(last != null && s.visible.isNotEmpty() && last >= s.visible.size - 5 && !s.loading && s.hasMore && s.error==null) viewModel.load()
        }
    }
    BackHandler(s.sourceLabel != null && !showText && overlay == null) { viewModel.restoreSource() }
    BackHandler(showText && overlay == null) { showText = false; firstToolbar.requestFocus() }
    val focused = s.visible.firstOrNull { it.preview.id == s.focusedId } ?: s.visible.firstOrNull()
    fun navigate(p: MetaPreview) { returning=true; onNavigateToDetail(p.id,p.apiType,p.sourceAddonBaseUrl.orEmpty()) }

    Column(Modifier.fillMaxSize().background(background).padding(horizontal=24.dp,vertical=12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("nuvio", color=Color.White,fontSize=20.sp)
            RoundControl(Icons.Default.Search,"סינון טקסט",s.query.isNotBlank(),Modifier.focusRequester(firstToolbar)) { showText=!showText }
            RoundControl(Icons.Default.FilterList,"פילטרים",s.filters.active) { overlay="filters" }
            RoundControl(Icons.Default.Sort,"מיון") { overlay="sort" }
            RoundControl(Icons.Default.LibraryBooks,"קטלוגים",s.catalog != null) { overlay="catalogs" }
            RoundControl(Icons.Default.ViewCarousel,"תצוגה: ${s.view.label()}") { viewModel.cycleView(); hint="${s.view.next().label()} · ${(s.view.next().ordinal+1)}/6" }
            RoundControl(Icons.Default.AspectRatio,"גודל פריטים") { viewModel.cycleSize(); hint="גודל: ${listOf("קטן","בינוני","גדול")[(s.size+1)%3]}" }
            RoundControl(Icons.Default.RestartAlt,"איפוס") { viewModel.reset(); hint="הפילטרים והקטלוג אופסו" }
            Spacer(Modifier.weight(1f))
            ContentSwitch(s.movie) { viewModel.selectType(!s.movie); showText=false; hint="הפילטרים והקטלוג אופסו" }
        }
        Box(Modifier.height(18.dp)) { hint?.let { Text(it,color=accent,fontSize=12.sp) } }
        if(showText) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(s.query, viewModel::text, Modifier.weight(1f).focusRequester(textFocus),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White),label={ Text("סינון בתוצאות שנטענו",color=Color.LightGray) })
            Action("×", { viewModel.text("") })
            Text("${s.visible.size} מתוך ${s.items.size} שנטענו",color=Color.LightGray,fontSize=12.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(s.sourceLabel ?: s.catalog?.let { "${it.catalogName} · ${it.addonName}" } ?: if(s.movie) "כל הסרטים" else "כל הסדרות",color=Color.White,fontSize=15.sp)
            if(s.localScope) Text("סינון / מיון בתוצאות שנטענו",color=accent,fontSize=11.sp)
        }
        if(s.filters.active) ActiveFilters(s.filters,viewModel::filter)
        if(s.error != null) Row(verticalAlignment=Alignment.CenterVertically) {
            Text(s.error!!,Modifier.weight(1f),color=Color.LightGray,fontSize=12.sp)
            Action("נסה שוב", viewModel::retry)
        }
        LaunchedEffect(s.view,s.size) {
            val index=s.visible.indexOfFirst { it.preview.id == s.focusedId }
            if(index >= 0) grid.scrollToItem(index)
        }
        val columns = when(s.view) {
            DiscoveryView.LIST -> 1
            DiscoveryView.POSTERS -> listOf(8,6,4)[s.size]
            DiscoveryView.CLEAR_LOGO -> listOf(5,4,3)[s.size]
            DiscoveryView.BANNERS -> listOf(3,2,1)[s.size]
            else -> listOf(4,3,2)[s.size]
        }
        if(s.visible.isEmpty() && !s.loading) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(if(s.query.isNotBlank()) "אין התאמות בתוצאות שנטענו" else "אין תוצאות עבור הבחירות האלה",color=Color.LightGray)
                if(s.hasMore) Action("טען עוד תוצאות לבדיקה", { viewModel.load() })
            }
        } else LazyVerticalGrid(GridCells.Fixed(columns),state=grid,modifier=Modifier.weight(1f),
            horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(s.visible,key={ it.preview.id }) { item ->
                val r = remember(item.preview.id) { FocusRequester() }
                DisposableEffect(item.preview.id) { requesters[item.preview.id]=r; onDispose { requesters.remove(item.preview.id) } }
                DiscoveryTile(item.preview,s.view,s.size,Modifier.focusRequester(r),onFocus={ viewModel.focus(item.preview.id) },
                    onClick={ navigate(item.preview) }, onHold={ actionItem=item.preview;overlay="actions" })
            }
        }
        Box(Modifier.height(18.dp)) { if(s.loading) Text("טוען עוד…",color=accent,fontSize=12.sp) }
        // Fixed reserved space prevents grid jumps as synopsis lengths change.
        if(s.view != DiscoveryView.CARDS) Box(Modifier.fillMaxWidth().height(66.dp)) {
            focused?.preview?.let { p -> Column {
                Text("${p.name}   ${p.releaseInfo.orEmpty()} · ${p.imdbRating ?: "—"}",color=Color.White,fontSize=16.sp,maxLines=1)
                Text(p.description.orEmpty(),color=Color.LightGray,fontSize=12.sp,maxLines=2)
            } }
        }
    }
    if(overlay != null) {
        DiscoveryOverlay(onClose={overlay=null}) {
            when(overlay) {
                "actions" -> actionItem?.let { p ->
                    Text(p.name,color=Color.White,fontSize=18.sp)
                    Action("פרטים",{overlay=null;navigate(p)})
                    Action("רשימת צפייה / נצפה",{overlay=null;viewModel.posterOptions.show(p,p.sourceAddonBaseUrl.orEmpty())})
                    Action("תוכן דומה",{overlay=null;viewModel.similar(p)})
                    if(s.movie) Action("שחקנים ובמאי",{overlay="people";viewModel.actorFromTitle(p)})
                }
                "people" -> {
                    Text("שחקנים ובמאי",color=Color.White,fontSize=18.sp)
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(s.choices) { c -> Action(c.name,{overlay=null;viewModel.chooseTitleActor(c)}) }
                    }
                }
                "catalogs" -> CatalogPicker(s) { viewModel.selectCatalog(it); overlay=null }
                "sort" -> SortPicker(s,viewModel::sort)
                "filters" -> FilterDrawer(s,viewModel::filter,viewModel::lookup)
            }
        }
    }
    val options by viewModel.posterOptions.state.collectAsState()
    com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(options,viewModel.posterOptions,
        onNavigateToDetail={id,type,url -> returning=true; onNavigateToDetail(id,type,url)})
}

@Composable
private fun RoundControl(icon: ImageVector,label: String,active: Boolean=false,modifier: Modifier=Modifier,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Box(modifier.size(34.dp).clip(CircleShape).background(if(active) accent.copy(alpha=.12f) else panel)
            .border(if(focused) 2.dp else 1.dp,if(focused) accent else Color(0xff344047),CircleShape)
            .onFocusChanged { focused=it.isFocused }.clickable(onClick=onClick).semantics { contentDescription=label; if(active) stateDescription="פעיל" },contentAlignment=Alignment.Center) {
            Icon(icon,label,Modifier.size(17.dp),tint=if(active || focused) accent else Color.White)
        }
        if(focused) androidx.compose.ui.window.Popup(alignment=Alignment.BottomCenter,
            offset=androidx.compose.ui.unit.IntOffset(0,28)) {
            Text(label,Modifier.background(panel,RoundedCornerShape(6.dp)).padding(5.dp),color=Color.White,fontSize=11.sp)
        }
    }
}
@Composable
private fun ContentSwitch(movie: Boolean,onClick:()->Unit) {
    val offset by animateDpAsState(if(movie) 25.dp else 3.dp,label="content switch")
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    Row(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick=onClick).padding(6.dp)
        .semantics { contentDescription="החלף סרטים וסדרות"; stateDescription=if(movie) "סרטים" else "סדרות" },
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("סדרות",color=if(!movie) accent else Color.Gray,fontSize=13.sp)
        // Absolute positioning keeps the left/right thumb consistent in RTL layouts.
        Box(Modifier.width(48.dp).height(24.dp).background(panel,CircleShape).border(1.dp,Color.DarkGray,CircleShape)) {
            Box(Modifier.absoluteOffset(x=offset,y=3.dp).size(18.dp).background(Color.White,CircleShape))
        }
        Text("סרטים",color=if(movie) accent else Color.Gray,fontSize=13.sp)
    }
    }
}
@Composable
private fun DiscoveryTile(p: MetaPreview,view: DiscoveryView,size: Int,modifier:Modifier,onFocus:()->Unit,onClick:()->Unit,onHold:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape=RoundedCornerShape(6.dp)
    val base=modifier.fillMaxWidth().onFocusChanged { focused=it.isFocused; if(it.isFocused) onFocus() }
        .clip(shape).background(if(view == DiscoveryView.CLEAR_LOGO) background else panel)
        .border(if(focused) 2.dp else 0.dp,if(focused) accent else Color.Transparent,shape)
        .combinedClickable(onClick=onClick,onLongClick=onHold).semantics { contentDescription=p.name }
    when(view) {
        DiscoveryView.CLEAR_LOGO -> Box(base.height(listOf(70,90,120)[size].dp).padding(12.dp),contentAlignment=Alignment.Center) {
            if(p.logo != null) AsyncImage(p.logo,p.name,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            else Text(p.name,color=Color.White,fontSize=listOf(15,18,22)[size].sp,maxLines=2)
        }
        DiscoveryView.LIST -> Row(base.height(listOf(52,66,88)[size].dp).padding(5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AsyncImage(p.poster,p.name,Modifier.heightIn(max=80.dp).aspectRatio(2f/3),contentScale=ContentScale.Crop)
            Text(p.name,Modifier.weight(1f),color=Color.White,maxLines=1)
            Text("${p.releaseInfo.orEmpty()} · ${p.imdbRating ?: "—"} · ${p.runtime ?: "—"}",color=Color.LightGray,fontSize=12.sp)
        }
        DiscoveryView.CARDS -> Column(base) {
            AsyncImage(p.backdropUrl,p.name,Modifier.fillMaxWidth().aspectRatio(16f/9),contentScale=ContentScale.Crop)
            Column(Modifier.padding(8.dp).height(70.dp)) {
                Text(p.name,color=Color.White,maxLines=1,fontSize=14.sp)
                Text("${p.releaseInfo.orEmpty()} · ${p.imdbRating ?: "—"}",color=Color.LightGray,fontSize=11.sp)
                if(focused) Text(p.description.orEmpty(),color=Color.LightGray,fontSize=11.sp,maxLines=2)
            }
        }
        else -> Box(base.aspectRatio(when(view) { DiscoveryView.POSTERS -> 2f/3; DiscoveryView.BANNERS -> 5.4f; else -> 16f/9 })) {
            AsyncImage(if(view == DiscoveryView.POSTERS) p.poster else p.landscapePoster ?: p.background ?: p.poster,p.name,
                Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            if(view != DiscoveryView.POSTERS) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha=.35f)).padding(5.dp),contentAlignment=Alignment.Center) {
                if(p.logo != null) AsyncImage(p.logo,p.name,Modifier.height(if(view == DiscoveryView.BANNERS) 28.dp else 34.dp).fillMaxWidth(),contentScale=ContentScale.Fit)
                else Text(p.name,color=Color.White,fontSize=13.sp,maxLines=1)
            }
        }
    }
}
@Composable
internal fun Action(label:String,onClick:()->Unit,active:Boolean=false) {
    var focus by remember { mutableStateOf(false) }
    Text(label,Modifier.clip(RoundedCornerShape(8.dp)).background(if(active) accent.copy(alpha=.15f) else panel)
        .border(if(focus) 2.dp else 0.dp,accent,RoundedCornerShape(8.dp)).onFocusChanged {focus=it.isFocused}
        .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=9.dp),color=if(active) accent else Color.White,fontSize=13.sp)
}
@Composable
private fun DiscoveryOverlay(onClose:()->Unit,content:@Composable ()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.CenterEnd) {
            Column(Modifier.width(330.dp).fillMaxHeight().background(panel).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Action("סגור ×",onClose)
                content()
            }
        }
    }
}
@Composable
private fun CatalogPicker(s:DiscoveryState,onSelect:(com.nuvio.tv.ui.screens.search.DiscoverCatalog?)->Unit) {
    Text("בחירת קטלוג",color=Color.White,fontSize=20.sp)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Action("כל התוכן",{onSelect(null)},s.catalog==null) }
        items(s.catalogs.filter { it.type == if(s.movie) "movie" else "series" },key={it.key}) { c ->
            Action("${c.catalogName}\n${c.addonName}",{onSelect(c)},s.catalog?.key==c.key)
        }
    }
}
@Composable
private fun SortPicker(s:DiscoveryState,onSort:(DiscoverySort,Boolean)->Unit) {
    Text("מיון",color=Color.White,fontSize=20.sp)
    Action(if(s.descending) "סדר יורד ↓" else "סדר עולה ↑",{onSort(s.sort,!s.descending)})
    DiscoverySort.entries.forEach { sort -> Action(sort.label(),{onSort(sort,s.descending)},s.sort==sort) }
    if(s.localScope) Text("המיון חל על התוצאות שנטענו",color=Color.LightGray,fontSize=12.sp)
}
@Composable
private fun ActiveFilters(f:DiscoveryFilters,onChange:(DiscoveryFilters)->Unit) {
    val chips=buildList<Pair<String,()->Unit>> {
        if(f.yearFrom!=null || f.yearTo!=null) add("${f.yearFrom ?: "…"}–${f.yearTo ?: "…"}" to {onChange(f.copy(yearFrom=null,yearTo=null))})
        if(f.genres.isNotEmpty()) add("ז׳אנרים (${f.genres.size})" to {onChange(f.copy(genres=emptySet()))})
        if(f.excludedGenres.isNotEmpty()) add("ז׳אנרים מוחרגים" to {onChange(f.copy(excludedGenres=emptySet()))})
        if(f.scoreFrom!=null || f.scoreTo!=null) add("ציון" to {onChange(f.copy(scoreFrom=null,scoreTo=null))})
        if(f.votes!=null) add("הצבעות ${f.votes}+" to {onChange(f.copy(votes=null))})
        if(f.runtimeFrom!=null || f.runtimeTo!=null) add("משך" to {onChange(f.copy(runtimeFrom=null,runtimeTo=null))})
        f.actors.forEach { a -> add(a.name to {onChange(f.copy(actors=f.actors-a))}) }
        f.company?.let {add(it.name to {onChange(f.copy(company=null))})}
        f.country?.let {add(it to {onChange(f.copy(country=null))})}
        f.language?.let {add(it to {onChange(f.copy(language=null))})}
        f.keyword?.let {add(it.name to {onChange(f.copy(keyword=null))})}
        f.certification?.let {add(it to {onChange(f.copy(certification=null))})}
        f.status?.let {add("סטטוס סדרה" to {onChange(f.copy(status=null))})}
        f.watched?.let {add("מצב צפייה" to {onChange(f.copy(watched=null))})}
    }
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        items(chips) { (name,remove) -> Action("$name ×",remove,true) }
        item {Action("נקה הכול",{onChange(DiscoveryFilters())})}
    }
}
@Composable
private fun FilterDrawer(s:DiscoveryState,onChange:(DiscoveryFilters)->Unit,onLookup:(String,String)->Unit) {
    val f=s.filters
    var lookupKind by remember {mutableStateOf<String?>(null)}
    var lookupQuery by remember {mutableStateOf("")}
    var genreMode by remember {mutableStateOf(false)}
    Text("סינון",color=Color.White,fontSize=20.sp)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Action("נקה פילטרים",{onChange(DiscoveryFilters())})}
        item {NumericRange("שנה",f.yearFrom?.toString().orEmpty(),f.yearTo?.toString().orEmpty(),
            {onChange(f.copy(yearFrom=it.toIntOrNull()))},{onChange(f.copy(yearTo=it.toIntOrNull()))})}
        item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            (1980..2020 step 10).forEach { y -> Action("${y}s",{onChange(f.copy(yearFrom=y,yearTo=y+9))},f.yearFrom==y && f.yearTo==y+9) }
        } }
        item {Action(if(genreMode) "הסתר ז׳אנרים" else "ז׳אנרים · כולם חייבים להתקיים",{genreMode=!genreMode})}
        if(genreMode) items(s.genres) { g -> Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            val id=g.id.toInt()
            Action(g.name,{onChange(f.copy(genres=if(id in f.genres) f.genres-id else f.genres+id,excludedGenres=f.excludedGenres-id))},id in f.genres)
            Action("ללא",{onChange(f.copy(excludedGenres=if(id in f.excludedGenres) f.excludedGenres-id else f.excludedGenres+id,genres=f.genres-id))},id in f.excludedGenres)
        } }
        item {NumericRange("ציון",f.scoreFrom?.toString().orEmpty(),f.scoreTo?.toString().orEmpty(),
            {onChange(f.copy(scoreFrom=it.toDoubleOrNull()?.coerceIn(0.0,10.0)))},{onChange(f.copy(scoreTo=it.toDoubleOrNull()?.coerceIn(0.0,10.0)))})}
        item {Field("מינימום הצבעות",f.votes?.toString().orEmpty(),{onChange(f.copy(votes=it.toIntOrNull()?.coerceAtLeast(0)))},true)}
        item {NumericRange(if(s.movie) "משך סרט בדקות" else "משך פרק בדקות",f.runtimeFrom?.toString().orEmpty(),f.runtimeTo?.toString().orEmpty(),
            {onChange(f.copy(runtimeFrom=it.toIntOrNull()?.coerceAtLeast(0)))},{onChange(f.copy(runtimeTo=it.toIntOrNull()?.coerceAtLeast(0)))})}
        if(s.movie) item {Action("שחקנים (${f.actors.size})",{lookupKind="actor";lookupQuery="";onLookup("actor","")})}
        if(s.movie && f.actors.isNotEmpty()) item {
            f.actors.forEach { a -> Action("${a.name} ×",{onChange(f.copy(actors=f.actors-a))},true) }
            Action(if(f.allActors) "כולם" else "לפחות אחד",{onChange(f.copy(allActors=!f.allActors))})
        }
        item {Action("אולפן: ${f.company?.name ?: "הכול"}",{lookupKind="company";lookupQuery="";onLookup("company","")})}
        item {Action("מדינה: ${s.countries.find{it.id==f.country}?.name ?: "הכול"}",{lookupKind="country";lookupQuery="";onLookup("country","")})}
        item {Action("שפה: ${s.languages.find{it.id==f.language}?.name ?: "הכול"}",{lookupKind="language";lookupQuery="";onLookup("language","")})}
        if(lookupKind!=null) {
            item {Field("חיפוש",lookupQuery,{lookupQuery=it;onLookup(lookupKind!!,it)})}
            item {Action("נקה בחירה",{
                onChange(when(lookupKind){"actor"->f.copy(actors=emptyList());"company"->f.copy(company=null);"country"->f.copy(country=null);else->f.copy(language=null)})
                lookupKind=null
            })}
            items(s.choices) { c -> Action(c.name,{
                onChange(when(lookupKind){"actor"->f.copy(actors=(f.actors+c).distinctBy{it.id});"company"->f.copy(company=c);"country"->f.copy(country=c.id);else->f.copy(language=c.id)})
                lookupQuery="";onLookup(lookupKind!!,"");lookupKind=null
            }) }
        }
        item {Text("נושאים",color=Color.LightGray)}
        items(s.topics) { topic -> Action(topic.name,{onChange(f.copy(keyword=if(f.keyword==topic) null else topic))},f.keyword==topic) }
        if(s.movie) {
            item {Text("דירוג גיל · ארה״ב (MPAA)",color=Color.LightGray)}
            item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("G","PG","PG-13","R","NC-17").forEach { rating -> Action(rating,{onChange(f.copy(certification=if(f.certification==rating) null else rating))},f.certification==rating) }
            }}
        } else item {Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("0" to "פעילה","3" to "הסתיימה","4" to "בוטלה").forEach { (id,label) -> Action(label,{onChange(f.copy(status=if(f.status==id) null else id))},f.status==id) }
        }}
        item {Text("מצב צפייה",color=Color.LightGray)}
        item {Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("watched" to "נצפה","unwatched" to "לא נצפה","saved" to "ברשימת הצפייה").forEach { (id,label) -> Action(label,{onChange(f.copy(watched=if(f.watched==id) null else id))},f.watched==id) }
        }}
    }
}
@Composable
private fun NumericRange(label:String,from:String,to:String,onFrom:(String)->Unit,onTo:(String)->Unit) {
    Column {Text(label,color=Color.LightGray,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)){Field("מ־",from,onFrom,true)};Box(Modifier.weight(1f)){Field("עד",to,onTo,true)}
    }}
}
@Composable
private fun Field(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    OutlinedTextField(if(editing) draft else value,{ draft=it; onChange(it) },Modifier.fillMaxWidth().onFocusChanged {
        if(it.hasFocus && !editing) draft=value
        editing=it.hasFocus
    },singleLine=true,
        textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=14.sp),
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),label={Text(label,color=Color.LightGray,fontSize=12.sp)})
}
