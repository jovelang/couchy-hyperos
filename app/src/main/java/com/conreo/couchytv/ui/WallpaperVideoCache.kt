// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** One bounded on-disk cache for wallpaper bytes; initialized on IO, never on an app click. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object WallpaperVideoCache {
    @Volatile private var cache:SimpleCache?=null
    fun usedBytes():Long=cache?.cacheSpace ?: 0L
    @Synchronized fun get(context:Context):SimpleCache {
        return cache ?: SimpleCache(File(context.cacheDir,"wallpaper-video"),
            LeastRecentlyUsedCacheEvictor(512L*1024*1024),StandaloneDatabaseProvider(context.applicationContext)).also {cache=it}
    }
}
