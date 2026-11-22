// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.data

import android.graphics.Bitmap
import android.graphics.Color as PixelColor
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.*

/** Artwork for the entire TV card, with no launcher-icon backplate. */
@Immutable
data class BrandArtwork(
    val top: Color, val bottom: Color, val ink: Color = Color.White,
    val mark: ImageBitmap? = null, val lockup: ImageBitmap? = null,
    val wordTop: ImageBitmap? = null, val wordBottom: ImageBitmap? = null,
    val wordmark: ImageBitmap? = null,
    val title: String = "", val textSize: Float = 20f, val markSize: Float = 38f,
    val logoWidth: Float = 122f, val logoHeight: Float = 38f,
    val calendar: Boolean = false,
)

/** Work runs during the off-main-thread app scan, not during composition. */
object BrandArtworkFactory {
    fun create(pkg: String, label: String, icon: Bitmap?, banner: Bitmap?, adaptive: Bitmap?): BrandArtwork {
        fun color(argb:Long)=Color(argb)
        val raw=adaptive ?: icon
        fun auto(source:Bitmap?=raw)=source?.let {RasterLogo.isolate(it)}?.asImageBitmap()
        fun white(source:Bitmap?=raw)=source?.let {RasterLogo.whiteInk(it)}?.asImageBitmap()
        fun region(source:Bitmap?,l:Float,t:Float,r:Float,b:Float)=source?.let {RasterLogo.region(it,l,t,r,b)}
        fun full(source:Bitmap?,white:Boolean=false)=source?.let {if(white) RasterLogo.whiteInk(it) else RasterLogo.isolate(it)}?.asImageBitmap()
        fun row(a:Long,b:Long,text:String=label,mark:ImageBitmap?=auto(),ink:Long=0xFFFFFFFF,size:Float=20f,markSize:Float=38f)=
            BrandArtwork(color(a),color(b),color(ink),mark=mark,title=text,textSize=size,markSize=markSize)
        fun logo(a:Long,b:Long,source:Bitmap?,white:Boolean=false,width:Float=122f,height:Float=38f)=
            BrandArtwork(color(a),color(b),lockup=full(source,white),title=label,logoWidth=width,logoHeight=height)
        return when(pkg) {
            "com.ktcp.video" -> {
                val glyph=region(banner,.05f,.20f,.30f,.80f)?.let {RasterLogo.isolate(it)}
                BrandArtwork(color(0xFFFFFEFC),color(0xFFF4F6F9),ink=color(0xFF161B23),mark=glyph?.asImageBitmap(),
                    title="腾讯视频",textSize=21f,markSize=36f)
            }
            "com.ugreen.nasprotv" -> BrandArtwork(color(0xFFEEF1F4),color(0xFFD8DFE5),
                mark=region(banner,.19f,.30f,.39f,.70f)?.let {RasterLogo.isolate(it).asImageBitmap()},
                wordmark=region(banner,.40f,.28f,.85f,.72f)?.let {RasterLogo.isolate(it).asImageBitmap()},title="绿联云",markSize=34f)
            "io.moreless.tide" -> logo(0xFFFFFAF7,0xFFF8ECE9,banner,width=116f,height=38f)
            "com.cibn.tv" -> logo(0xFFFFFCF5,0xFFF5F0E3,banner,width=119f,height=34f)
            "com.gentv.tv" -> logo(0xFF22384B,0xFF152635,banner,width=119f,height=35f)
            "com.vasthread.webviewtv.v2" -> logo(0xFF3277DF,0xFF1853B8,banner,true,width=116f,height=42f)
            "com.oversea.aslauncher" -> logo(0xFFDA20E5,0xFF6922EB,banner,true,width=119f,height=34f)
            "com.emotn.browser" -> logo(0xFF03AFCE,0xFF0399B7,banner,true,width=120f,height=38f)
            "com.xiaodianshi.tv.yst" -> {
                val glyph=region(banner,.2f,.06f,.8f,.65f)?.let {RasterLogo.whiteInk(it)}
                BrandArtwork(color(0xFFFF6994),color(0xFFED4376),mark=glyph?.asImageBitmap(),
                    title="Bilibili",textSize=22f,markSize=36f)
            }
            "com.netease.uuremote" -> row(0xFFF9FCFF,0xFFE9F2FE,"UU远程",ink=0xFF193B6B,size=21f)
            "com.aios.osbot.tv" -> row(0xFFFFF3E9,0xFFF4DED4,"Xiaomi\nmiclaw",ink=0xFF423B4B,size=18f,markSize=40f)
            "blbl.cat3399" -> row(0xFFF1E9FF,0xFFE4D5FA,"BLBL",ink=0xFF6732A7,size=22f)
            "io.github.sds100.keymapper" -> row(0xFFF3F7FE,0xFFE1EAF8,"Key\nMapper",ink=0xFF223B5F,size=19f)
            "com.duanju.zhenguojian.lite" -> row(0xFFFF856C,0xFFFF7057,"真果",auto(region(icon,.35f,.28f,.70f,.72f)),ink=0xFF22262E,size=23f,markSize=31f)
            "com.duanju.duanju_app" -> row(0xFFFF7D69,0xFFFA6655,"红果",auto(region(icon,.35f,.28f,.70f,.72f)),ink=0xFF22262E,size=23f,markSize=31f)
            "rocks.aston.hongguotv", "rocks.aston.hongguotv.mpv" -> row(0xFFF95387,0xFFD22D63,
                if(pkg.endsWith(".mpv")) "红果 MPV" else "红果短剧",white(icon),size=18f,markSize=30f)
            "com.douyin.tv" -> row(0xFF25262C,0xFF0D0E12,"抖音",auto(),size=23f,markSize=40f)
            "com.ucmobile.yun.tv" -> row(0xFFFF9A32,0xFFEF6B17,"UC 网盘",white(region(icon,.03f,0f,.97f,.76f)),size=20f,markSize=40f)
            "com.tencent.start.tv" -> row(0xFF15364D,0xFF081F35,"START\n云游戏",size=20f,markSize=40f)
            "com.android.vending" -> row(0xFF344352,0xFF202D3A,"Google\nPlay",size=20f,markSize=38f)
            "com.fongmi.android.tv" -> row(0xFF295066,0xFF163344,"影视",size=23f,markSize=39f)
            "com.xiaomi.mitv.smartshare" -> row(0xFF3BAA82,0xFF1A8D70,"无线投屏",auto(),size=18f)
            "com.xiaomi.mitv.tvmanager" -> row(0xFF31AE7C,0xFF128B65,"电视管家",auto(),size=18f)
            "com.mitv.gallery" -> row(0xFF8C7DE9,0xFF586BD8,"相册",size=23f)
            "com.tcl.browser" -> row(0xFF4B7DE2,0xFF3157B5,"Browse\nHere",size=19f,markSize=36f)
            "com.xiaomi.voicecontrol" -> row(0xFF253E5D,0xFF12283F,"小爱同学",size=18f)
            "com.vonchange.utao.gecko" -> row(0xFFF0C997,0xFFD9A16F,"土拨鼠\n浏览器",icon?.let {RasterLogo.mascot(it).asImageBitmap()},ink=0xFF49341F,size=19f,markSize=42f)
            "com.xiaomi.mitv.calendar" -> BrandArtwork(color(0xFFEAF4FF),color(0xFFCEE4FC),color(0xFF20518D),title="日历",calendar=true)
            "com.conreo.couchytv" -> row(0xFF5CA79A,0xFF357F78,"Couchy",size=20f)
            else -> {
                val palette=RasterLogo.palette(raw)
                BrandArtwork(palette.first,palette.second,mark=auto(),title=label,textSize=if(label.length>6) 17f else 20f)
            }
        }
    }
}

/** Removes icon packaging while retaining source brand geometry and lettering. */
object RasterLogo {
    private data class Rgb(val r:Float,val g:Float,val b:Float)
    fun region(src:Bitmap,l:Float,t:Float,r:Float,b:Float):Bitmap {
        val x=(src.width*l).toInt().coerceIn(0,src.width-1)
        val y=(src.height*t).toInt().coerceIn(0,src.height-1)
        return Bitmap.createBitmap(src,x,y,(src.width*r).toInt().coerceAtMost(src.width)-x,(src.height*b).toInt().coerceAtMost(src.height)-y)
    }
    private fun corners(src:Bitmap,p:IntArray):List<Rgb?> {
        val w=src.width;val h=src.height;val cw=max(2,w/8);val ch=max(2,h/8)
        return listOf(0 to 0,w-cw to 0,0 to h-ch,w-cw to h-ch).map {(ox,oy)->
            val rs=ArrayList<Int>();val gs=ArrayList<Int>();val bs=ArrayList<Int>()
            for(y in oy until oy+ch) for(x in ox until ox+cw) {
                val c=p[y*w+x]
                if(PixelColor.alpha(c)>230) {rs.add(PixelColor.red(c));gs.add(PixelColor.green(c));bs.add(PixelColor.blue(c))}
            }
            if(rs.size<cw*ch/8) null else {rs.sort();gs.sort();bs.sort();Rgb(rs[rs.size/2].toFloat(),gs[gs.size/2].toFloat(),bs[bs.size/2].toFloat())}
        }
    }
    private fun background(c:List<Rgb?>,x:Float,y:Float):Rgb {
        val fallback=c.firstOrNull {it!=null} ?: Rgb(255f,255f,255f)
        val a=c[0]?:fallback;val b=c[1]?:fallback;val d=c[2]?:fallback;val e=c[3]?:fallback
        fun mix(u:Float,v:Float,s:Float,t:Float)=(u*(1-x)+v*x)*(1-y)+(s*(1-x)+t*x)*y
        return Rgb(mix(a.r,b.r,d.r,e.r),mix(a.g,b.g,d.g,e.g),mix(a.b,b.b,d.b,e.b))
    }
    fun isolate(src:Bitmap):Bitmap {
        val w=src.width;val h=src.height;val pixels=IntArray(w*h);src.getPixels(pixels,0,w,0,0,w,h)
        val c=corners(src,pixels)
        if(c.all {it==null}) return trim(src)
        for(y in 0 until h) for(x in 0 until w) {
            val i=y*w+x;val p=pixels[i];val original=PixelColor.alpha(p)
            if(original==0) continue
            val bg=background(c,x.toFloat()/max(1,w-1),y.toFloat()/max(1,h-1))
            val dr=PixelColor.red(p)-bg.r;val dg=PixelColor.green(p)-bg.g;val db=PixelColor.blue(p)-bg.b
            val distance=sqrt((dr*dr+dg*dg+db*db)/3f)
            val opacity=((distance-10f)/24f).coerceIn(0f,1f)
            val a=(original*opacity).roundToInt()
            pixels[i]=(p and 0x00FFFFFF) or (a shl 24)
        }
        return trim(Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888))
    }
    fun whiteInk(src:Bitmap):Bitmap {
        val w=src.width;val h=src.height;val pixels=IntArray(w*h);src.getPixels(pixels,0,w,0,0,w,h)
        for(i in pixels.indices) {
            val p=pixels[i];val r=PixelColor.red(p);val g=PixelColor.green(p);val b=PixelColor.blue(p)
            val low=min(r,min(g,b));val high=max(r,max(g,b))
            val value=((low-155f)/100f).coerceIn(0f,1f)
            val neutral=(1f-(high-low)/75f).coerceIn(0f,1f)
            val alpha=(PixelColor.alpha(p)*value*neutral).roundToInt()
            pixels[i]=(alpha shl 24) or 0x00FFFFFF
        }
        return trim(Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888))
    }
    fun trim(src:Bitmap):Bitmap {
        val w=src.width;val h=src.height;val p=IntArray(w*h);src.getPixels(p,0,w,0,0,w,h)
        var left=w;var top=h;var right=-1;var bottom=-1
        for(y in 0 until h) for(x in 0 until w) if(PixelColor.alpha(p[y*w+x])>20) {
            left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
        }
        if(right<left || bottom<top) return Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
        val pad=1;left=(left-pad).coerceAtLeast(0);top=(top-pad).coerceAtLeast(0);right=(right+pad).coerceAtMost(w-1);bottom=(bottom+pad).coerceAtMost(h-1)
        return Bitmap.createBitmap(src,left,top,right-left+1,bottom-top+1)
    }
    fun splitWord(src:Bitmap,fraction:Float):Pair<Bitmap,Bitmap>? {
        if(src.width<4) return null
        val center=(src.width*fraction).roundToInt()
        val margin=max(2,src.width/30)
        val cut=((center-margin).coerceAtLeast(1)..(center+margin).coerceAtMost(src.width-1)).minByOrNull {x->
            var sum=0L;for(y in 0 until src.height) sum+=PixelColor.alpha(src.getPixel(x,y));sum
        } ?: center
        return trim(Bitmap.createBitmap(src,0,0,cut,src.height)) to trim(Bitmap.createBitmap(src,cut,0,src.width-cut,src.height))
    }
    fun mascot(original:Bitmap):Bitmap {
        val src=region(original,.04f,.04f,.96f,.97f)
        val w=src.width;val h=src.height;val p=IntArray(w*h);src.getPixels(p,0,w,0,0,w,h)
        val removed=BooleanArray(p.size);val queue=IntArray(p.size);var head=0;var tail=0
        fun matte(i:Int):Boolean {
            if(PixelColor.alpha(p[i])<20) return true
            val r=PixelColor.red(p[i]);val g=PixelColor.green(p[i]);val b=PixelColor.blue(p[i])
            return (r>150 && g in 21..144 && b<145 && r>g*1.08f) || (g>120 && g>r*1.1f && b<140)
        }
        fun add(i:Int) {if(!removed[i] && matte(i)) {removed[i]=true;queue[tail++]=i}}
        for(x in 0 until w) {add(x);if(x<w*.18f || x>w*.88f) add((h-1)*w+x)}
        for(y in 0 until h) {add(y*w);add(y*w+w-1)}
        while(head<tail) {
            val i=queue[head++];val x=i%w;val y=i/w
            if(x>0)add(i-1);if(x<w-1)add(i+1);if(y>0)add(i-w);if(y<h-1)add(i+w)
        }
        for(i in p.indices) if(removed[i]) p[i]=0
        return trim(Bitmap.createBitmap(p,w,h,Bitmap.Config.ARGB_8888))
    }
    fun palette(src:Bitmap?):Pair<Color,Color> {
        if(src==null) return Color(0xFF426080) to Color(0xFF253A52)
        val bins=HashMap<Int,Int>();val hsv=FloatArray(3)
        for(y in 0 until src.height step 3) for(x in 0 until src.width step 3) {
            val p=src.getPixel(x,y)
            if(PixelColor.alpha(p)<100) continue
            PixelColor.colorToHSV(p,hsv)
            if(hsv[1]<.25f || hsv[2]<.25f) continue
            val hue=(hsv[0]/15).toInt();bins[hue]=(bins[hue]?:0)+1
        }
        val hue=(bins.maxByOrNull {it.value}?.key ?: 14)*15f
        return Color(PixelColor.HSVToColor(floatArrayOf(hue,.55f,.66f))) to Color(PixelColor.HSVToColor(floatArrayOf(hue,.65f,.45f)))
    }
}
