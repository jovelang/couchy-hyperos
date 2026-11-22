// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import com.conreo.couchytv.data.AppEntry
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlin.math.floor

/** Shared card geometry; the original brand artwork and palette give each app its identity. */
@Composable
fun BrandCard(app:AppEntry) {
    val art=app.artwork
    if(isSystemArtwork(app)) { SystemAppArtwork(app);return }
    if(art==null) return
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(art.top,art.bottom))),contentAlignment=Alignment.Center) {
        when {
            art.calendar -> {
                val day by produceState(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()) {
                    while(true) {
                        delay(60_000)
                        value=Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                    Icon(AppIcons.Calendar,null,Modifier.size(36.dp),tint=art.ink)
                    Column {
                        Text(day+"日",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=art.ink)
                        Text("日历",fontSize=13.sp,color=art.ink)
                    }
                }
            }
            art.lockup!=null -> Image(art.lockup,app.label,
                Modifier.width(art.logoWidth.dp).height(art.logoHeight.dp),contentScale=ContentScale.Fit)
            else -> {
                Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                    art.mark?.let { Image(it,null,Modifier.size(art.markSize.dp),contentScale=ContentScale.Fit) }
                    if(art.wordTop!=null && art.wordBottom!=null) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Image(art.wordTop,null,Modifier.fillMaxWidth().height(12.dp),contentScale=ContentScale.Fit,alignment=Alignment.CenterStart)
                            Image(art.wordBottom,app.label,Modifier.fillMaxWidth().height(22.dp),contentScale=ContentScale.Fit,alignment=Alignment.CenterStart)
                        }
                    } else if(art.wordmark!=null) {
                        Image(art.wordmark,app.label,Modifier.weight(1f).height(23.dp),
                            contentScale=ContentScale.Fit,alignment=Alignment.CenterStart)
                    } else FittedBrandName(art.title,art.textSize,art.ink,Modifier.weight(1f))
                }
            }
        }
    }
}

/** Fit intentional lines to the available space instead of breaking a brand name mid-word. */
@Composable
private fun FittedBrandName(title:String,targetSize:Float,color:Color,modifier:Modifier) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    BoxWithConstraints(modifier) {
        val width=with(density) {maxWidth.roundToPx()}
        val style=remember(title,targetSize,width,density) {
            fun atSize(size:Float)=TextStyle(fontSize=size.sp,lineHeight=(size+3).sp,
                fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,letterSpacing=0.sp)
            val natural=measurer.measure(title,atSize(targetSize),softWrap=false,maxLines=2).size.width
            val fitted=if(natural>width) floor(targetSize*width/natural*.99f*2)/2 else targetSize
            atSize(fitted.coerceIn(12f,targetSize))
        }
        Text(title,color=color,style=style,softWrap=false,maxLines=2)
    }
}
