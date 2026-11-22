// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Catalog copied from Apple's signed macOS DesktopPicture MobileAsset catalog.
 * Images are cached only after selection; no third-party executable source or scripts. */
object AppleWallpapers {
    @Serializable data class Entry(val name:String,val url:String)
    fun entries(ctx:Context):List<Entry> {
        val offline=listOf(Entry("极光 · 本地渐变","gradient:aurora"),Entry("海洋 · 本地渐变","gradient:ocean"),Entry("暮色 · 本地渐变","gradient:dusk"))
        val online=runCatching {Json.decodeFromString<List<Entry>>(ctx.assets.open("apple-wallpapers.json").bufferedReader().use {it.readText()})}.getOrDefault(emptyList())
        return offline+online
    }
    fun decode(ctx:Context,uri:String):Bitmap? {
        if(uri.startsWith("gradient:")) {
            val colors=when(uri.substringAfter(':')) {
                "ocean"->intArrayOf(0xFF092A49.toInt(),0xFF126F88.toInt(),0xFF54B3A0.toInt())
                "dusk"->intArrayOf(0xFF2E214A.toInt(),0xFF754A7A.toInt(),0xFFD1927F.toInt())
                else->intArrayOf(0xFF10283F.toInt(),0xFF266A70.toInt(),0xFF7AC5A4.toInt())
            }
            return Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888).also {bitmap->
                val paint=android.graphics.Paint().apply {shader=android.graphics.LinearGradient(0f,0f,1920f,1080f,colors,null,android.graphics.Shader.TileMode.CLAMP)}
                android.graphics.Canvas(bitmap).drawRect(0f,0f,1920f,1080f,paint)
            }
        }
        if(uri.startsWith("https://updates.cdn-apple.com/"))return decode(ctx,obtain(ctx,Entry("Apple",uri)))
        val bytes=if(uri.startsWith("asset:"))ctx.assets.open("wallpapers/"+uri.substringAfter(':')).use {it.readBytes()}
            else ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {input->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(32768)
                while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=32*1024*1024){"Image too large"};out.write(buffer,0,n)}
                out.toByteArray()
            } ?: return null
        return boundedDecode(bytes)
    }
    private fun boundedDecode(bytes:ByteArray):Bitmap? {
        val options=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        var sample=1;while(options.outWidth/sample>1920*2 || options.outHeight/sample>1080*2)sample*=2
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample})?.let {
            val scale=minOf(1f,1920f/it.width,1080f/it.height)
            if(scale<1f)Bitmap.createScaledBitmap(it,(it.width*scale).toInt().coerceAtLeast(1),(it.height*scale).toInt().coerceAtLeast(1),true).also {_ ->it.recycle()} else it
        }?.also {it.prepareToDraw()}
    }
    fun obtain(ctx:Context,e:Entry):String {
        if(e.url.startsWith("asset:") || e.url.startsWith("gradient:"))return e.url
        require(e.url.startsWith("https://updates.cdn-apple.com/"))
        val dir=File(ctx.cacheDir,"apple-wallpapers").apply {mkdirs()}
        val file=File(dir,e.url.hashCode().toUInt().toString(16)+".png")
        if(file.exists()){file.setLastModified(System.currentTimeMillis());return Uri.fromFile(file).toString()}
        val conn=URL(e.url).openConnection() as HttpURLConnection
        conn.connectTimeout=10000;conn.readTimeout=15000
        try {
            require(conn.responseCode==200);require(conn.contentLengthLong<=64L*1024*1024)
            var total=0L;var bitmap:Bitmap?=null
            ZipInputStream(conn.inputStream).use {zip->
                while(bitmap==null) {
                    val entry=zip.nextEntry ?: break
                    if(entry.name.lowercase().endsWith(".heic") || entry.name.lowercase().endsWith(".jpg") || entry.name.lowercase().endsWith(".png")) {
                        val out=java.io.ByteArrayOutputStream();val block=ByteArray(32768)
                        while(true){val n=zip.read(block);if(n<0)break;total+=n;require(total<=96L*1024*1024);out.write(block,0,n)}
                        bitmap=boundedDecode(out.toByteArray())
                    }
                    zip.closeEntry()
                }
            }
            val image=requireNotNull(bitmap){"No supported image"}
            val tmp=File(dir,file.name+".tmp");tmp.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};check(tmp.renameTo(file));image.recycle()
            var used=dir.listFiles()?.sumOf {it.length()} ?: 0L
            dir.listFiles()?.sortedBy {it.lastModified()}?.forEach {f->if(used>128L*1024*1024 && f!=file){val n=f.length();if(f.delete())used-=n}}
            return Uri.fromFile(file).toString()
        } finally {conn.disconnect()}
    }
}
