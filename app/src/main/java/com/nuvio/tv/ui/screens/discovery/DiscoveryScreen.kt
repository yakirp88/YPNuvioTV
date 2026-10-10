@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateContentSize
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

private val accent: Color @Composable get() = com.nuvio.tv.ui.theme.NuvioTheme.colors.Secondary
private val background: Color @Composable get() = com.nuvio.tv.ui.theme.NuvioTheme.colors.Background
private val panel: Color @Composable get() = com.nuvio.tv.ui.theme.NuvioTheme.colors.Surface
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
    var lastFilter by rememberSaveable { mutableStateOf("year") }
    var gridHasFocus by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
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
    val columns = when(s.view) {
        DiscoveryView.LIST -> 1
        DiscoveryView.POSTERS -> listOf(8,6,4)[s.size]
        DiscoveryView.CLEAR_LOGO -> listOf(5,4,3)[s.size]
        DiscoveryView.BANNERS -> listOf(3,2,1)[s.size]
        else -> listOf(4,3,2)[s.size]
    }
    // Deliberately trigger only when the user has reached the end, never drain a catalog
    // automatically merely because a local text filter has zero matches.
    LaunchedEffect(grid, s.visible.size, s.hasMore, s.loading,s.error,s.focusedId,columns) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.distinctUntilChanged().collect { last ->
            if(last != null && s.visible.isNotEmpty() && discoveryShouldPrefetch(s.visible.indexOfFirst { it.preview.id == s.focusedId },s.visible.size,columns) && !s.loading && s.hasMore && s.error==null) viewModel.load()
        }
    }
    BackHandler(s.sourceLabel != null && !showText && overlay == null) { viewModel.restoreSource() }
    BackHandler(showText && overlay == null) { showText = false; firstToolbar.requestFocus() }
    LaunchedEffect(s.focusedId,s.infoPosition,s.expansionDelay,gridHasFocus) {
        expandedId=null
        if(s.infoPosition==DiscoveryInfoPosition.EXPAND && gridHasFocus && s.focusedId!=null) {
            delay(s.expansionDelay*1000L);expandedId=s.focusedId
        }
    }
    val focused = s.visible.firstOrNull { it.preview.id == s.focusedId } ?: s.visible.firstOrNull()
    fun navigate(p: MetaPreview) { returning=true; onNavigateToDetail(p.id,p.apiType,p.sourceAddonBaseUrl.orEmpty()) }

    Column(Modifier.fillMaxSize().background(background).padding(horizontal=24.dp,vertical=12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("nuvio", color=Color.White,fontSize=20.sp)
            RoundControl(Icons.Default.Search,"סינון טקסט",s.query.isNotBlank(),Modifier.focusRequester(firstToolbar)) { showText=!showText }
            Box {
                RoundControl(Icons.Default.FilterList,"פילטרים",s.filters.active) { overlay="filter-menu" }
                if(overlay?.startsWith("filter")==true) FilterMenu(s,lastFilter,overlay!!,
                    onFocusKind={lastFilter=it},onSelect={kind->lastFilter=kind;overlay="filter:$kind";viewModel.lookup(kind,"")},
                    onBack={overlay="filter-menu"},onClose={overlay=null},onChange=viewModel::filter,onLookup=viewModel::lookup)
            }
            Box {
                RoundControl(Icons.Default.Sort,"מיון") { overlay="sort-menu" }
                if(overlay=="sort-menu") ToolbarMenu({overlay=null}) {
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Action("עולה ↑",{viewModel.sort(descending=false)},!s.descending)
                        Action("יורד ↓",{viewModel.sort(descending=true)},s.descending)
                    }
                    DiscoverySort.entries.forEach { sort -> Action(sort.label(),{viewModel.sort(sort)},s.sort==sort) }
                }
            }
            RoundControl(Icons.Default.LibraryBooks,"קטלוגים",s.catalog != null) { overlay="catalogs" }
            RoundControl(Icons.Default.ViewCarousel,"תצוגה: ${s.view.label()}") { viewModel.cycleView(); hint="${s.view.next().label()} · ${(s.view.next().ordinal+1)}/6" }
            RoundControl(Icons.Default.AspectRatio,"גודל פריטים") { viewModel.cycleSize(); hint="גודל: ${listOf("קטן","בינוני","גדול")[(s.size+1)%3]}" }
            RoundControl(Icons.Default.Save,"ייצוא לקטלוג") { overlay="export" }
            RoundControl(Icons.Default.RestartAlt,"איפוס") { viewModel.reset(); hint="הפילטרים והקטלוג אופסו" }
            Spacer(Modifier.weight(1f))
            ContentSwitch(s.movie) { viewModel.selectType(!s.movie); showText=false; hint="הפילטרים והקטלוג אופסו" }
        }
        Box(Modifier.height(30.dp)) { hint?.let { Text(it,color=accent,fontSize=12.sp) } }
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
        if(s.infoPosition==DiscoveryInfoPosition.TOP) DiscoveryInfo(focused?.preview)
        if(s.visible.isEmpty() && !s.loading && s.metadataPending==0) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(if(s.query.isNotBlank()) "אין התאמות בתוצאות שנטענו" else "אין תוצאות עבור הבחירות האלה",color=Color.LightGray)
                if(s.hasMore) Action("טען עוד תוצאות לבדיקה", { viewModel.load() })
            }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val tileWidth=(maxWidth-10.dp*(columns-1))/columns
            val rowHeight=when(s.view) {
                DiscoveryView.POSTERS -> tileWidth*1.5f
                DiscoveryView.CLEAR_LOGO -> listOf(70,90,120)[s.size].dp
                DiscoveryView.LIST -> listOf(52,66,88)[s.size].dp
                DiscoveryView.CARDS -> tileWidth*9f/16f+86.dp
                DiscoveryView.BANNERS -> tileWidth/5.4f
                else -> tileWidth*9f/16f
            }
            val middle=s.infoPosition==DiscoveryInfoPosition.MIDDLE
            val anchorTop=if(middle) (maxHeight/2-rowHeight).coerceAtLeast(0.dp) else 0.dp
            val focusIndex=s.visible.indexOfFirst {it.preview.id==s.focusedId}.coerceAtLeast(0)
            val selectedRow=focusIndex/columns
            LaunchedEffect(middle,selectedRow,s.view,s.size) {
                if(middle) grid.scrollToItem(selectedRow*columns)
            }
            LazyVerticalGrid(GridCells.Fixed(columns),state=grid,modifier=Modifier.fillMaxSize(),
                contentPadding=PaddingValues(top=anchorTop,bottom=if(middle) maxHeight/2 else 0.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                s.visible.forEachIndexed { index,entry ->
                    item(key=entry.preview.id,span={GridItemSpan(if(expandedId==entry.preview.id) columns.coerceAtMost(2) else 1)}) {
                        val r = remember(entry.preview.id) { FocusRequester() }
                        DisposableEffect(entry.preview.id) { requesters[entry.preview.id]=r; onDispose { requesters.remove(entry.preview.id) } }
                        DiscoveryTile(entry.preview,s.view,s.size,Modifier.focusRequester(r),expanded=expandedId==entry.preview.id,
                            onFocus={viewModel.focus(entry.preview.id)},onFocusState={gridHasFocus=it},
                            onClick={navigate(entry.preview)},onHold={actionItem=entry.preview;overlay="actions"})
                    }
                    if(middle && (index==((selectedRow+1)*columns-1).coerceAtMost(s.visible.lastIndex))) {
                        item(key="fixed-info",span={GridItemSpan(maxLineSpan)}) { DiscoveryInfo(focused?.preview) }
                    }
                }
            }
        }
        if(s.infoPosition==DiscoveryInfoPosition.BOTTOM) DiscoveryInfo(focused?.preview)
        s.exportMessage?.let { Text(it,color=accent,fontSize=12.sp) }
        if(s.exporting) Action("בטל ייצוא",viewModel::cancelExport)
        Box(Modifier.height(18.dp)) { if(s.loading || s.metadataPending>0) Text(if(s.loading) "טוען עוד…" else "משלים פרטים…",color=accent,fontSize=12.sp) }
    }
    if(overlay != null && overlay!="filter-menu" && overlay!="sort-menu" && !(overlay?.startsWith("filter:")==true && overlay!!.substringAfter(':') in inlineFilterKinds)) {
        DiscoveryOverlay(onClose={overlay=if(overlay?.startsWith("filter:")==true) "filter-menu" else null},back=overlay?.startsWith("filter:")==true) {
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
                "export" -> {
                    var catalogName by remember { mutableStateOf("") }
                    Text("ייצוא הסינון לקטלוג",color=Color.White,fontSize=14.sp)
                    Text("איסוף כל התוצאות שהמקור מאפשר לקבל. הקטלוג יישמר באוספים ויוצמד למעלה.",color=Color.LightGray,fontSize=12.sp)
                    Field("שם הקטלוג",catalogName,{catalogName=it})
                    Action("אסוף ושמור",{if(catalogName.isNotBlank()) {viewModel.exportCatalog(catalogName);overlay=null}})
                }
                "catalogs" -> CatalogPicker(s) { viewModel.selectCatalog(it); overlay=null }
                "sort" -> SortPicker(s,viewModel::sort)
                else -> if(overlay?.startsWith("filter:")==true) FilterEditor(overlay!!.substringAfter(':'),s,viewModel::filter,viewModel::lookup)
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
        if(focused) androidx.compose.ui.window.Popup(alignment=Alignment.TopCenter,
            offset=androidx.compose.ui.unit.IntOffset(0,with(androidx.compose.ui.platform.LocalDensity.current){42.dp.roundToPx()})) {
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
private fun DiscoveryTile(p: MetaPreview,view: DiscoveryView,size: Int,modifier:Modifier,expanded:Boolean=false,onFocus:()->Unit,onFocusState:(Boolean)->Unit,onClick:()->Unit,onHold:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape=RoundedCornerShape(6.dp)
    val base=modifier.fillMaxWidth().onFocusChanged { focused=it.isFocused; onFocusState(it.isFocused); if(it.isFocused) onFocus() }
        .clip(shape).background(if(view == DiscoveryView.CLEAR_LOGO) background else panel)
        .border(if(focused) 2.dp else 0.dp,if(focused) accent else Color.Transparent,shape)
        .combinedClickable(onClick=onClick,onLongClick=onHold).semantics { contentDescription=p.name }
    if(expanded) {
        Column(base.animateContentSize()) {
            AsyncImage(p.background ?: p.landscapePoster ?: p.poster,p.name,Modifier.fillMaxWidth().aspectRatio(16f/9),contentScale=ContentScale.Crop)
            DiscoveryInfo(p,compact=true)
        }
        return
    }
    when(view) {
        DiscoveryView.CLEAR_LOGO -> Box(base.height(listOf(70,90,120)[size].dp).padding(12.dp),contentAlignment=Alignment.Center) {
            var imageFailed by remember(p.logo) {mutableStateOf(false)}
            if(!p.logo.isNullOrBlank() && !imageFailed) AsyncImage(p.logo,p.name,Modifier.fillMaxSize(),contentScale=ContentScale.Fit,onError={imageFailed=true})
            else Text(p.name,color=Color.White,fontSize=listOf(13,15,18)[size].sp,maxLines=2)
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
internal fun Action(label:String,onClick:()->Unit,active:Boolean=false,modifier:Modifier=Modifier) {
    var focus by remember { mutableStateOf(false) }
    Text(label,modifier.clip(RoundedCornerShape(4.dp)).background(if(focus) accent.copy(alpha=.12f) else Color.Transparent)
        .onFocusChanged {focus=it.isFocused}.clickable(onClick=onClick).padding(horizontal=8.dp,vertical=6.dp),
        color=if(active || focus) accent else com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary,fontSize=12.sp,maxLines=1)
}
@Composable
private fun DiscoveryInfo(p:MetaPreview?,compact:Boolean=false) {
    Box(Modifier.fillMaxWidth().height(if(compact) 54.dp else 66.dp).background(panel.copy(alpha=.45f)).padding(8.dp)) {
        p?.let {Column {
            Text("${it.name}   ${it.releaseInfo.orEmpty()} · ${it.imdbRating ?: "—"}",color=com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary,fontSize=14.sp,maxLines=1)
            Text(it.description.orEmpty(),color=com.nuvio.tv.ui.theme.NuvioTheme.colors.TextSecondary,fontSize=11.sp,maxLines=if(compact) 1 else 2)
        }}
    }
}
@Composable
private fun DiscoveryOverlay(onClose:()->Unit,back:Boolean=false,content:@Composable ()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Column(Modifier.width(500.dp).heightIn(max=420.dp).clip(RoundedCornerShape(18.dp)).background(panel).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Action(if(back) "חזרה" else "סגור ×",onClose)
                content()
            }
        }
    }
}
@Composable
private fun CatalogPicker(s:DiscoveryState,onSelect:(com.nuvio.tv.ui.screens.search.DiscoverCatalog?)->Unit) {
    Text("בחירת קטלוג",color=Color.White,fontSize=14.sp)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Action("כל התוכן",{onSelect(null)},s.catalog==null) }
        items(s.catalogs.filter { it.type == if(s.movie) "movie" else "series" },key={it.key}) { c ->
            Action("${c.catalogName} · ${c.addonName}",{onSelect(c)},s.catalog?.key==c.key)
        }
    }
}
@Composable
private fun SortPicker(s:DiscoveryState,onSort:(DiscoverySort,Boolean)->Unit) {
    Text("מיון",color=Color.White,fontSize=14.sp)
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
private fun filterKinds(movie:Boolean) = buildList {
    add("year" to "שנה ועשור");add("genre" to "ז׳אנר");add("score" to "ציון")
    if(movie) add("actor" to "שחקנים")
    add("company" to "אולפן");add("country" to "מדינה");add("language" to "שפת מקור")
    add("topic" to "נושא וסגנון");add(if(movie) "age" to "דירוג גיל" else "status" to "סטטוס סדרה")
    add("watched" to "מצב צפייה")
}
private val inlineFilterKinds=setOf("genre","topic","age","status","watched")
@Composable
private fun FilterMenu(s:DiscoveryState,lastKind:String,overlay:String,onFocusKind:(String)->Unit,
    onSelect:(String)->Unit,onBack:()->Unit,onClose:()->Unit,onChange:(DiscoveryFilters)->Unit,onLookup:(String,String)->Unit) {
    val kinds=filterKinds(s.movie)
    val requesters=remember(s.movie) { kinds.associate { it.first to FocusRequester() } }
    val trash=remember {FocusRequester()}
    LaunchedEffect(overlay) { if(overlay=="filter-menu") {
        withFrameNanos { };runCatching {(requesters[lastKind] ?: requesters.values.first()).requestFocus()}
    } }
    val direction=androidx.compose.ui.platform.LocalLayoutDirection.current
    ToolbarMenu(onClose,focusable=overlay=="filter-menu") {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                ResetFiltersButton({onChange(DiscoveryFilters())},modifier=Modifier.focusRequester(trash)
                    .focusProperties {right=requesters[lastKind] ?: requesters.values.first()}
                    .semantics {contentDescription="איפוס כל הפילטרים"})
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
                    Column(Modifier.weight(1f)) { kinds.forEach { (kind,label) ->
                        Box {
                            Action(label,{onSelect(kind)},modifier=Modifier.fillMaxWidth().focusRequester(requesters.getValue(kind))
                                .focusProperties {left=trash}.onFocusChanged {if(it.isFocused) onFocusKind(kind)})
                            if(overlay=="filter:$kind" && kind in inlineFilterKinds) AdjacentMenu(onBack) {
                                FilterEditor(kind,s,onChange,onLookup)
                            }
                        }
                    } }
                }
            }
        }
    }
}
@Composable
private fun ResetFiltersButton(onClick:()->Unit,modifier:Modifier=Modifier) {
    var focused by remember {mutableStateOf(false)}
    Box(modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
        .background(if(focused) accent.copy(alpha=.16f) else Color.Transparent)
        .onFocusChanged {focused=it.isFocused}.clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        Icon(Icons.Default.DeleteOutline,"איפוס כל הפילטרים",Modifier.size(17.dp),tint=if(focused) accent else Color.LightGray)
    }
}
@Composable
private fun AdjacentMenu(onBack:()->Unit,content:@Composable ()->Unit) {
    val gap=with(androidx.compose.ui.platform.LocalDensity.current){6.dp.roundToPx()}
    val position=remember(gap) {object:androidx.compose.ui.window.PopupPositionProvider {
        override fun calculatePosition(anchorBounds:androidx.compose.ui.unit.IntRect,windowSize:androidx.compose.ui.unit.IntSize,
            layoutDirection:androidx.compose.ui.unit.LayoutDirection,popupContentSize:androidx.compose.ui.unit.IntSize):androidx.compose.ui.unit.IntOffset =
            androidx.compose.ui.unit.IntOffset((anchorBounds.left-popupContentSize.width-gap).coerceAtLeast(0),
                anchorBounds.top.coerceIn(0,(windowSize.height-popupContentSize.height).coerceAtLeast(0)))
    } }
    androidx.compose.ui.window.Popup(popupPositionProvider=position,onDismissRequest=onBack,
        properties=androidx.compose.ui.window.PopupProperties(focusable=true)) {
        Column(Modifier.width(190.dp).heightIn(max=320.dp).background(panel,RoundedCornerShape(8.dp)).padding(8.dp)) {content()}
    }
}
@Composable
private fun ToolbarMenu(onClose:()->Unit,focusable:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Popup(alignment=Alignment.TopStart,offset=androidx.compose.ui.unit.IntOffset(0,with(androidx.compose.ui.platform.LocalDensity.current){42.dp.roundToPx()}),
        onDismissRequest=onClose,properties=androidx.compose.ui.window.PopupProperties(focusable=focusable)) {
        Column(Modifier.width(190.dp).heightIn(max=320.dp).clip(RoundedCornerShape(8.dp)).background(panel)
            .verticalScroll(rememberScrollState()).padding(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp),content=content)
    }
}
@Composable
private fun FilterEditor(kind:String,s:DiscoveryState,onChange:(DiscoveryFilters)->Unit,onLookup:(String,String)->Unit) {
    val f=s.filters
    var query by remember(kind) {mutableStateOf("")}
    Text(filterKinds(s.movie).find { it.first==kind }?.second.orEmpty(),color=Color.White,fontSize=14.sp)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        when(kind) {
            "year" -> {
                item {NumericRange("שנה",f.yearFrom?.toString().orEmpty(),f.yearTo?.toString().orEmpty(),
                    {onChange(f.copy(yearFrom=it.toIntOrNull()))},{onChange(f.copy(yearTo=it.toIntOrNull()))})}
                item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    (1950..2020 step 10).forEach { y -> Action("${y}s",{onChange(f.copy(yearFrom=y,yearTo=y+9))},f.yearFrom==y && f.yearTo==y+9) }
                }}
                item {Action("כל השנים",{onChange(f.copy(yearFrom=null,yearTo=null))})}
            }
            "score" -> {
                item {NumericRange("ציון",f.scoreFrom?.toString().orEmpty(),f.scoreTo?.toString().orEmpty(),
                    {onChange(f.copy(scoreFrom=it.toDoubleOrNull()?.coerceIn(0.0,10.0)))},{onChange(f.copy(scoreTo=it.toDoubleOrNull()?.coerceIn(0.0,10.0)))})}
                item {Action("כל הציונים",{onChange(f.copy(scoreFrom=null,scoreTo=null))})}
            }
            "genre" -> {
                item {Text("✓ כלול · − החרג · ○ ללא סינון",color=Color.LightGray,fontSize=10.sp)}
                items(s.genres) {g -> val id=g.id.toInt();val mark=if(id in f.genres) "✓" else if(id in f.excludedGenres) "−" else "○"
                    Action("${g.name}  $mark",{onChange(f.cycleGenre(id))},id in f.genres || id in f.excludedGenres,Modifier.fillMaxWidth())
                }
            }
            "actor","company","country","language" -> {
                item {Field("חיפוש",query,{query=it;onLookup(kind,it)})}
                if(kind=="actor") {
                    item {Action(if(f.allActors) "כולם" else "לפחות אחד",{onChange(f.copy(allActors=!f.allActors))})}
                    items(f.actors) {c -> Action("${c.name} ×",{onChange(f.copy(actors=f.actors-c))},true)}
                }
                item {Action("נקה בחירה",{onChange(when(kind){"actor"->f.copy(actors=emptyList());"company"->f.copy(company=null);"country"->f.copy(country=null);else->f.copy(language=null)})})}
                val choices=when(kind) {"country"->s.countries.filter {it.name.contains(query,true)||it.id.contains(query,true)};"language"->s.languages.filter{it.name.contains(query,true)||it.id.contains(query,true)};else->s.choices}
                items(choices,key={it.id}) {c -> Action(c.name,{onChange(when(kind){"actor"->f.copy(actors=(f.actors+c).distinctBy{it.id});"company"->f.copy(company=c);"country"->f.copy(country=c.id);else->f.copy(language=c.id)})},
                    when(kind){"actor"->c in f.actors;"company"->c==f.company;"country"->c.id==f.country;else->c.id==f.language})}
            }
            "topic" -> items(s.topics) {c -> Action(c.name,{onChange(f.copy(keyword=if(f.keyword==c) null else c))},f.keyword==c)}
            "age" -> {
                item {Text("דירוג MPAA · ארה״ב",color=Color.LightGray)}
                items(listOf("G","PG","PG-13","R","NC-17")) {r -> Action(r,{onChange(f.copy(certification=if(f.certification==r) null else r))},f.certification==r)}
            }
            "status" -> items(listOf("0" to "פעילה","3" to "הסתיימה","4" to "בוטלה")) {(id,label) -> Action(label,{onChange(f.copy(status=if(f.status==id) null else id))},f.status==id)}
            "watched" -> items(listOf("watched" to "נצפה","unwatched" to "לא נצפה","saved" to "ברשימת הצפייה")) {(id,label) -> Action(label,{onChange(f.copy(watched=if(f.watched==id) null else id))},f.watched==id)}
        }
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
    },singleLine=true,colors=androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor=accent,cursorColor=accent,focusedLabelColor=accent,
        unfocusedBorderColor=com.nuvio.tv.ui.theme.NuvioTheme.colors.TextSecondary),
        textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=14.sp),
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),label={Text(label,color=Color.LightGray,fontSize=12.sp)})
}
