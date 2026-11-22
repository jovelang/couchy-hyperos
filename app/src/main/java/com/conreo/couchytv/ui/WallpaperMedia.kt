// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Public media only, bounded disk cache and normal platform TLS validation. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object WallpaperMedia {
    private val client=okhttp3.OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS)
        .readTimeout(10,TimeUnit.SECONDS).build()
    fun factory(context:Context):DataSource.Factory {
        val http=OkHttpDataSource.Factory(client)
        val source=runCatching {CacheDataSource.Factory()
            .setCache(WallpaperVideoCache.get(context)).setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) as DataSource.Factory}
            .getOrElse {android.util.Log.w("CouchyVideo","disk cache unavailable; streaming directly",it);http}
        return DefaultDataSource.Factory(context.applicationContext,source)
    }
    suspend fun prefetch(context:Context,uri:String) = runInterruptible(Dispatchers.IO) {
        // CacheWriter requires CacheDataSource; local document URIs need no network prefetch.
        if(!uri.startsWith("https://") && !uri.startsWith("http://")) return@runInterruptible
        val cached=CacheDataSource.Factory().setCache(WallpaperVideoCache.get(context))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(client)).createDataSource()
        val writer=CacheWriter(cached,DataSpec.Builder().setUri(uri).setLength(2L*1024*1024).build(),ByteArray(64*1024),null)
        try {writer.cache()} finally {cached.close()}
        // Read top-level ISO-BMFF atom headers, not the media payload. Cache the
        // complete movie index before handing over the decoder (moov may be at EOF).
        if(!uri.substringBefore('?').lowercase().let {it.endsWith(".mov") || it.endsWith(".mp4") || it.endsWith(".m4v")})return@runInterruptible
        fun source()=CacheDataSource.Factory().setCache(WallpaperVideoCache.get(context))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(client)).createDataSource()
        var position=0L
        repeat(32) {
            val header=ByteArray(16);val read=source()
            try {
                read.open(DataSpec.Builder().setUri(uri).setPosition(position).setLength(16).build())
                var offset=0
                while(offset<16) {val n=read.read(header,offset,16-offset);if(n<0)break;offset+=n}
                if(offset<8)return@runInterruptible
            } finally {read.close()}
            val bytes=java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.BIG_ENDIAN)
            var size=bytes.int.toLong() and 0xffffffffL
            val type=String(header,4,4,Charsets.US_ASCII)
            if(size==1L) {bytes.position(8);size=bytes.long}
            if(size<8 || size>16L*1024*1024*1024)return@runInterruptible
            if(type=="moov") {
                require(size<=16L*1024*1024){"Movie index is too large"}
                val index=source()
                try {CacheWriter(index,DataSpec.Builder().setUri(uri).setPosition(position).setLength(size).build(),ByteArray(64*1024),null).cache()}
                finally {index.close()}
                return@runInterruptible
            }
            position=Math.addExact(position,size)
        }
    }

    private fun folder(context:Context)=File(context.cacheDir,"wallpaper-first-frames").apply {mkdirs()}
    private fun key(uri:String)=MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") {"%02x".format(it)}
    fun first(context:Context,uri:String):Bitmap? {
        val f=File(folder(context),key(uri)+".png")
        return if(f.exists()) BitmapFactory.decodeFile(f.path)?.also {f.setLastModified(System.currentTimeMillis());it.prepareToDraw()} else null
    }
    fun saveFirst(context:Context,uri:String,bitmap:Bitmap,setAsCurrent:Boolean=true) {
        if(setAsCurrent)context.getSharedPreferences("wallpaper-media",Context.MODE_PRIVATE).edit().putString("current-uri",uri).apply()
        val dir=folder(context);val file=File(dir,key(uri)+".png")
        if(!file.exists()) {
            val tmp=File(dir,file.name+".tmp")
            tmp.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            check(tmp.renameTo(file))
            WallpaperPlaybackState.firstFrameCaptures++
        }
        if(setAsCurrent)listOf("last-video-frame.png","last-video-frame.jpg","last-video-frame.tmp").forEach {File(context.filesDir,it).delete()}
        val files=dir.listFiles()?.filter {it.extension=="png"}?.sortedBy {it.lastModified()} ?: return
        var bytes=files.sumOf {it.length()}
        for(f in files) if(bytes>64L*1024*1024 && f!=file) {val n=f.length();if(f.delete())bytes-=n}
    }
    fun currentFirstFile(context:Context):File? {
        val uri=context.getSharedPreferences("wallpaper-media",Context.MODE_PRIVATE).getString("current-uri","").orEmpty()
        return if(uri.isEmpty())null else File(folder(context),key(uri)+".png").takeIf {it.exists()}
    }
    fun currentFirst(context:Context):Bitmap?=currentFirstFile(context)?.let {BitmapFactory.decodeFile(it.path)?.also {it.prepareToDraw()}}
    fun firstFrameBytes(context:Context)=folder(context).listFiles()?.sumOf {it.length()} ?: 0L
}
