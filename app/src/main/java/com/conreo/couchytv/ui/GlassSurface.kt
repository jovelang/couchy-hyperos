// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.*
import kotlin.math.max

val LocalGlassFrame = compositionLocalOf<ImageBitmap?> { null }
val LocalGlassRoot = staticCompositionLocalOf { IntSize(1920,1080) }

/** Frost the matching crop of the wallpaper; never blur menu text or its icons. */
@Composable
fun GlassSurface(modifier: Modifier = Modifier, light: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    // Immediate stable tint, dark enough to separate menu text from the wallpaper.
    val shape=remember {androidx.compose.foundation.shape.RoundedCornerShape(22.dp)}
    Box(modifier.loopingMenuNavigation().clip(shape).background(if(light) Color(0xE6F6F7FA) else Color(0xDF121820))
        .border(1.dp,Color.White.copy(alpha=if(light) .45f else .26f),shape)) {
        CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides if(light) Color(0xFF171A20) else Color.White) {content()}
    }
}

/** Request once when its focus node and window are ready; never steal focus later. */
suspend fun androidx.compose.ui.focus.FocusRequester.requestWhenReady() {
    repeat(30) {
        withFrameNanos {}
        val focused=runCatching {
            requestFocus()
            val acquired=captureFocus()
            if(acquired) freeFocus()
            acquired
        }.getOrDefault(false)
        if(focused) return
    }
}
