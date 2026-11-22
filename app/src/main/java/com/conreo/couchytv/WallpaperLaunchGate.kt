// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

/** Main-thread hook: pause the wallpaper before opening an external app. */
object WallpaperLaunchGate {
    private class Entry(val snapshot:()->android.graphics.Bitmap?,val callback:(Boolean)->Unit)
    private val owners=mutableListOf<Entry>()
    fun install(snapshot:()->android.graphics.Bitmap?={null},handler:(Boolean)->Unit):()->Unit {
        val entry=Entry(snapshot,handler)
        owners.add(entry)
        return {owners.remove(entry)}
    }
    /** One in-memory handoff frame, never a periodic capture or a disk screenshot. */
    fun frame():android.graphics.Bitmap?=runCatching {owners.lastOrNull()?.snapshot?.invoke()}.getOrNull()
    fun prepare() {owners.lastOrNull()?.callback?.invoke(true)}
    fun failed() {owners.lastOrNull()?.callback?.invoke(false)}
}
