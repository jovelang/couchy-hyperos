// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.content.Context
import android.graphics.Bitmap

/** Only first-frame covers; no periodic screenshots or live menu samples. */
object VideoFrameCache {
    @Volatile private var frame:Bitmap?=null
    const val glassCopies=0
    fun use(bitmap:Bitmap) {frame=bitmap}
    fun peek():Bitmap?=frame
    fun load(context:Context):Bitmap?=frame ?: WallpaperMedia.currentFirst(context)?.also {frame=it}
}
