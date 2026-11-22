// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.conreo.couchytv.data.CalendarLabels
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.data.LauncherConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.serialization.json.*

/** ADB-only configuration access; other installed applications cannot read or write it. */
class ConfigProvider : ContentProvider() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override fun onCreate() = true
    private fun checkCaller() {
        if (Binder.getCallingUid() != Process.myUid()) {
            requireNotNull(context).enforceCallingPermission(android.Manifest.permission.DUMP, "ADB only")
        }
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        checkCaller()
        val ctx = requireNotNull(context)
        return when (method) {
            "patch" -> {
                val patch=json.parseToJsonElement(requireNotNull(arg)).jsonObject
                runBlocking {ConfigStore(ctx).update {current->
                    val original=json.encodeToJsonElement(LauncherConfig.serializer(),current).jsonObject
                    require(patch.keys.all {it in original}) {"Unknown setting"}
                    json.decodeFromJsonElement(LauncherConfig.serializer(),JsonObject(original+patch)).copy(setupDone=true)
                }}
                Bundle().apply {putBoolean("updated",true)}
            }
            "remote-apps" -> {
                val pm=ctx.packageManager
                val found=linkedMapOf<String,android.content.pm.ResolveInfo>()
                listOf(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER,android.content.Intent.CATEGORY_LAUNCHER).forEach {category->
                    pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(category),0).forEach {info->
                        found.putIfAbsent(info.activityInfo.packageName,info)
                    }
                }
                val apps=buildJsonArray {found.forEach {(pkg,info)->add(buildJsonObject {
                    put("package",pkg)
                    put("label",when(pkg){"com.ktcp.video"->"腾讯视频";"com.xiaodianshi.tv.yst"->"Bilibili";else->info.loadLabel(pm).toString()})
                    put("component",android.content.ComponentName(pkg,info.activityInfo.name).flattenToShortString())
                    put("system",(info.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0)
                })}}
                Bundle().apply {putString("apps_json",apps.toString())}
            }
            "playback-state" -> Bundle().apply {
                putString("uri",com.conreo.couchytv.ui.WallpaperPlaybackState.uri)
                putBoolean("repeating",com.conreo.couchytv.ui.WallpaperPlaybackState.repeating)
                putBoolean("covered",com.conreo.couchytv.ui.WallpaperPlaybackState.covered)
                putBoolean("switching",com.conreo.couchytv.ui.WallpaperPlaybackState.switching.value)
                putInt("first_frame_captures",com.conreo.couchytv.ui.WallpaperPlaybackState.firstFrameCaptures)
                putInt("surface_binds",com.conreo.couchytv.ui.WallpaperPlaybackState.surfaceBinds)
                putInt("dropped_frames",com.conreo.couchytv.ui.WallpaperPlaybackState.droppedFrames)
                putInt("rendered_frames",com.conreo.couchytv.ui.WallpaperPlaybackState.renderedFrames)
                putLong("first_frame_cache_bytes",com.conreo.couchytv.ui.WallpaperMedia.firstFrameBytes(ctx))
                val latch=java.util.concurrent.CountDownLatch(1)
                val sampled=java.util.concurrent.atomic.AtomicReference<Bundle?>()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching {sampled.set(com.conreo.couchytv.ui.WallpaperPlaybackState.inspect?.invoke())}
                    latch.countDown()
                }
                if(latch.await(1,java.util.concurrent.TimeUnit.SECONDS))sampled.get()?.let {putAll(it)}
            }
            "saver-status" -> Bundle().apply {
                putString("implementation",ScreenPower.implementation());putBoolean("available",ScreenPower.available());putBoolean("system_suppressed",ScreenPower.suppressed)
                putString("screen_on",ScreenPower.screenOn(ctx).toString())
                putBoolean("active",com.conreo.couchytv.ui.ScreenSaverRuntime.active.value)
                putBoolean("external",com.conreo.couchytv.ui.ScreenSaverRuntime.external.value)
                putBoolean("system_integrated",ScreenPower.integrated.value)
                putString("last_error",ScreenPower.lastError)
            }
            "saver-preview" -> Bundle().apply {com.conreo.couchytv.ui.ScreenSaverRuntime.preview();putBoolean("started",true)}
            "saver-system-test" -> Bundle().apply {putBoolean("requested",SystemIdleReceiver.route(ctx))}
            "saver-bridge-probe" -> Bundle().apply {putBoolean("requested",ScreenPower.launchSystemIdleSaver(ctx,probe=true))}
            "idle-status" -> Bundle().apply {
                putBoolean("desktop_foreground",LauncherVisibility.resumed)
                putBoolean("audio_playing",ctx.getSystemService(android.media.AudioManager::class.java).isMusicActive)
                putLong("idle_ms",android.os.SystemClock.elapsedRealtime()-com.conreo.couchytv.ui.ScreenSaverRuntime.lastInput)
                putBoolean("saver_active",com.conreo.couchytv.ui.ScreenSaverRuntime.active.value)
                putBoolean("system_integrated",ScreenPower.integrated.value)
            }
            "display-probe" -> Bundle().apply {putBoolean("requested",ScreenPower.displayProbe(ctx))}
            "saver-standby-test" -> Bundle().apply {putBoolean("requested",ScreenPower.standby())}
            "power-command-probe" -> Bundle().apply {putBoolean("requested",ScreenPower.commandProbe())}
            "saver-wake" -> Bundle().apply {com.conreo.couchytv.ui.ScreenSaverRuntime.wake();putBoolean("woke",true)}
            "picture-off-test" -> Bundle().apply {
                putBoolean("requested",runBlocking {ScreenPower.pictureOff(ctx)})
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ScreenPower.wakeDisplay()},10_000)
            }
            "cache-status" -> Bundle().apply {
                val file = com.conreo.couchytv.ui.WallpaperMedia.currentFirstFile(ctx)
                    ?: java.io.File(ctx.filesDir, "last-video-frame.png").takeIf {it.exists()}
                    ?: java.io.File(ctx.filesDir,"last-video-frame.jpg")
                putBoolean("video_frame_saved", file.exists())
                putLong("video_frame_bytes", file.length())
                val frame=com.conreo.couchytv.ui.VideoFrameCache.peek()
                putInt("video_frame_width",frame?.width ?: 0)
                putInt("video_frame_height",frame?.height ?: 0)
                putString("video_frame_format",if(file.extension=="png") "PNG" else "JPEG")
                putInt("glass_frame_copies",com.conreo.couchytv.ui.VideoFrameCache.glassCopies)
                putLong("video_cache_bytes",com.conreo.couchytv.ui.WallpaperVideoCache.usedBytes())
            }
            "import" -> {
                val cfg = json.decodeFromString(LauncherConfig.serializer(), requireNotNull(arg))
                runBlocking { ConfigStore(ctx).update { cfg.copy(setupDone = true) } }
                Bundle().apply { putBoolean("imported", true) }
            }
            "date-preview" -> {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(requireNotNull(arg))!!
                Bundle().apply {
                    putString("solar", CalendarLabels.solar(date, 7, Locale.CHINA))
                    putString("lunar", CalendarLabels.lunar(date))
                }
            }
            else -> throw IllegalArgumentException("Unknown operation")
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        checkCaller()
        val cfg = runBlocking { ConfigStore(requireNotNull(context)).flow.first() }
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(json.encodeToString(LauncherConfig.serializer(), cfg))) }
    }
    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
        checkCaller()
        require(mode=="r")
        val parts=uri.pathSegments
        require(parts.size==3 && parts[0]=="artwork" && parts[1].matches(Regex("[a-zA-Z0-9_.]+")) && parts[2] in setOf("icon","banner","mark","lockup","word-top","word-bottom","wordmark"))
        val ctx=requireNotNull(context);val pkg=parts[1];val pm=ctx.packageManager
        val component=(pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))?.component
        val info=component?.let {pm.getActivityInfo(it,0)}
        if(parts[2] in setOf("mark","lockup","word-top","word-bottom","wordmark")) {
            val apps=com.conreo.couchytv.data.AppRepository.memoryCache ?: runBlocking {com.conreo.couchytv.data.AppRepository.scan(ctx)}
            val art=requireNotNull(apps.firstOrNull {it.pkg==pkg}?.artwork)
            val image=when(parts[2]) {"mark"->art.mark;"lockup"->art.lockup;"word-top"->art.wordTop;"wordmark"->art.wordmark;else->art.wordBottom}
            val bmp=requireNotNull(image).asAndroidBitmap()
            val file=java.io.File(ctx.cacheDir,"artwork-$pkg-${parts[2]}.png")
            file.outputStream().use {bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            return android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val drawable=if(parts[2]=="banner") info?.loadBanner(pm) ?: pm.getApplicationIcon(pkg) else info?.loadIcon(pm) ?: pm.getApplicationIcon(pkg)
        val bmp=drawable.toBitmap(if(parts[2]=="banner")384 else 128,if(parts[2]=="banner")216 else 128)
        val file=java.io.File(ctx.cacheDir,"artwork-$pkg-${parts[2]}.png")
        file.outputStream().use {bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        return android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
}
