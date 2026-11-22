// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

/** Read-only ADB diagnostics; no UI work is performed by the diagnostic provider. */
object WallpaperPlaybackState {
    @Volatile var surfaceBinds=0
    @Volatile var inspect:(()->android.os.Bundle)?=null
    val switching=kotlinx.coroutines.flow.MutableStateFlow(false)
    @Volatile var firstFrameCaptures=0
    @Volatile var positionMs=0L
    @Volatile var droppedFrames=0
    @Volatile var renderedFrames=0
    @Volatile var uri=""
    @Volatile var repeating=false
    @Volatile var covered=true
}
