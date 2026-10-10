@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.formatHeroRuntime
import kotlinx.coroutines.delay

/** The same artwork, typography and metadata treatment as native Modern Home. */
@Composable
internal fun DiscoveryHero(p: MetaPreview?, tmdbScore: Double?, hasImdb: Boolean, side: Boolean, modifier: Modifier = Modifier) {
    val colors=NuvioTheme.colors
    if(p==null) {Spacer(modifier);return}
    val direction=LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.clipToBounds()) {
            if(side) {
                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f/9)) {
                        AsyncImage(p.backdropUrl,p.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,colors.Background))))
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        HeroInformation(p,tmdbScore,hasImdb,true,Modifier.fillMaxWidth())
                    }
                }
            } else {
                Box(Modifier.fillMaxHeight().fillMaxWidth(.6f)) {
                    AsyncImage(p.backdropUrl,p.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent,colors.Background))))
                }
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    HeroInformation(p,tmdbScore,hasImdb,false,Modifier.align(Alignment.CenterEnd).fillMaxWidth(.54f).padding(start=10.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroInformation(p:MetaPreview,tmdbScore:Double?,hasImdb:Boolean,side:Boolean,modifier:Modifier) {
    val colors=NuvioTheme.colors
    var failed by remember(p.logo) {mutableStateOf(false)}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(!p.logo.isNullOrBlank() && !failed) AsyncImage(p.logo,p.name,
            Modifier.height(if(side) 58.dp else 48.dp).widthIn(max=220.dp).fillMaxWidth(),
            contentScale=ContentScale.Fit,alignment=Alignment.CenterStart,onError={failed=true})
        else Text(p.name,color=colors.TextPrimary,style=androidx.tv.material3.MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
        val metadata=remember(p.released,p.releaseInfo,p.runtime,p.genres) {
            listOfNotNull(p.released?.take(10) ?: p.releaseInfo,formatHeroRuntime(p.runtime),p.genres.take(2).joinToString(" / ").takeIf {it.isNotBlank()}).joinToString(" · ")
        }
        if(metadata.isNotBlank()) Text(metadata,color=colors.TextSecondary,fontSize=11.sp,maxLines=2)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            p.ageRating?.takeIf {it.isNotBlank()}?.let {Text(it,color=colors.TextSecondary,fontSize=11.sp)}
            if(hasImdb) p.imdbRating?.takeIf {it>0}?.let {Text("IMDb ${String.format(java.util.Locale.ROOT,"%.1f",it)}",color=colors.TextPrimary,fontSize=11.sp)}
            if(!hasImdb) tmdbScore?.takeIf {it>0}?.let {Text("TMDB ${String.format(java.util.Locale.ROOT,"%.1f",it)}",color=colors.TextSecondary,fontSize=11.sp)}
        }
        ScrollingSynopsis(p.id,p.description.orEmpty(),if(side) 112 else 72)
    }
}

@Composable
private fun ScrollingSynopsis(id:String,text:String,height:Int) {
    val scroll=rememberScrollState()
    LaunchedEffect(id,text) {
        scroll.scrollTo(0)
        delay(3000)
        while(true) {
            if(scroll.maxValue>0 && scroll.maxValue<Int.MAX_VALUE) {
                scroll.animateScrollTo(scroll.maxValue,androidx.compose.animation.core.tween(
                    durationMillis=(scroll.maxValue*45).coerceIn(2500,30000),easing=androidx.compose.animation.core.LinearEasing))
                delay(2500)
                scroll.scrollTo(0)
            }
            delay(3000)
        }
    }
    Box(Modifier.fillMaxWidth().height(height.dp).clipToBounds()) {
        Text(text,Modifier.fillMaxWidth().verticalScroll(scroll,enabled=false),color=NuvioTheme.colors.TextSecondary,fontSize=12.sp,lineHeight=15.sp)
    }
}
