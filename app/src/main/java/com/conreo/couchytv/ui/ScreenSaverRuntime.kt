// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conreo.couchytv.ScreenPower
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import com.conreo.couchytv.data.LauncherConfig
import kotlin.math.*

object ScreenSaverRuntime {
    val active=MutableStateFlow(false)
    val external=MutableStateFlow(false)
    val handoffFrame=MutableStateFlow<android.graphics.Bitmap?>(null)
    @Volatile var externalStarts=0L
    @Volatile var lastInput=SystemClock.elapsedRealtime()
    @Volatile var startedAt=0L
    @Volatile var offAt=0L
    private var swallowed=-1
    private fun capture() {com.conreo.couchytv.WallpaperLaunchGate.frame()?.let {handoffFrame.value=it}}
    fun wake() {
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()) {android.os.Handler(android.os.Looper.getMainLooper()).post {wake()};return}
        if(active.value)capture()
        active.value=false;lastInput=SystemClock.elapsedRealtime();startedAt=0;offAt=0;ScreenPower.wakeDisplay()
    }
    fun preview() {
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()) {android.os.Handler(android.os.Looper.getMainLooper()).post {preview()};return}
        if(!active.value)capture()
        startedAt=SystemClock.elapsedRealtime();active.value=true
    }
    fun onKey(event:KeyEvent):Boolean {
        if(event.keyCode==swallowed) {if(event.action==KeyEvent.ACTION_UP)swallowed=-1;return true}
        lastInput=SystemClock.elapsedRealtime()
        if(active.value || ScreenPower.dark.value) {if(event.action==KeyEvent.ACTION_DOWN) {swallowed=event.keyCode;wake()};return true}
        return false
    }
}

@Composable
fun ScreenSaverHost(config:LauncherConfig,menuOpen:Boolean,standalone:Boolean=false,onExit:()->Unit={}) {
    val ctx=LocalContext.current;val owner=LocalLifecycleOwner.current
    val external by ScreenSaverRuntime.external.collectAsState()
    if(external && !standalone)return
    val active by ScreenSaverRuntime.active.collectAsStateWithLifecycle()
    val frame by ScreenSaverRuntime.handoffFrame.collectAsState()
    val dark by ScreenPower.dark.collectAsStateWithLifecycle()
    val token=remember {android.os.Binder()}
    val activity=remember(ctx) {generateSequence(ctx) {(it as? android.content.ContextWrapper)?.baseContext}.filterIsInstance<android.app.Activity>().firstOrNull()}
    var powerFailed by remember(config.saverEnabled,config.saverOwner,config.saverScreenOffMinutes) {mutableStateOf(false)}
    val custom=(config.saverEnabled && config.saverOwner==1) || standalone
    var foreground by remember {mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    DisposableEffect(owner,custom) {
        val observer=LifecycleEventObserver {_,event->when(event) {
            Lifecycle.Event.ON_START->{
                foreground=true
                val own=custom || ScreenSaverRuntime.active.value
                ScreenPower.suppressSystem(ctx,own,token)
                if(own)activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if(!standalone)ScreenSaverRuntime.lastInput=SystemClock.elapsedRealtime()
            }
            Lifecycle.Event.ON_STOP->{
                // A stopped Compose host may not run its effects until it resumes.
                // Release here so Xiaomi can detect idle in the foreground app.
                foreground=false
                ScreenPower.suppressSystem(ctx,false,token)
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if(!ScreenSaverRuntime.external.value || standalone)ScreenSaverRuntime.wake()
            }
            else->{}
        }}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);ScreenPower.suppressSystem(ctx,false,token);activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    LaunchedEffect(active,custom,foreground) {
        val own=foreground && (custom || active)
        ScreenPower.suppressSystem(ctx,own,token)
        if(own)activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    LaunchedEffect(custom,config.saverAfterMinutes,config.saverScreenOffMinutes,config.saverStandbyMinutes,menuOpen,foreground) {
        if(!custom || !foreground)return@LaunchedEffect
        val audio=ctx.getSystemService(android.media.AudioManager::class.java)
        while(true) {
            delay(1000)
            val now=SystemClock.elapsedRealtime()
            if(!ScreenSaverRuntime.active.value && (menuOpen || audio.isMusicActive)) {ScreenSaverRuntime.lastInput=now;continue}
            if(!standalone && !ScreenSaverRuntime.active.value && config.saverAfterMinutes>0 && now-ScreenSaverRuntime.lastInput>=config.saverAfterMinutes*60_000L)ScreenSaverRuntime.preview()
            if(!powerFailed && ScreenSaverRuntime.active.value && ScreenSaverRuntime.offAt==0L && config.saverScreenOffMinutes>0 && now-ScreenSaverRuntime.startedAt>=config.saverScreenOffMinutes*60_000L) {
                if(ScreenPower.pictureOff(ctx))ScreenSaverRuntime.offAt=now else if(ScreenSaverRuntime.active.value) {powerFailed=true;com.conreo.couchytv.Actions.toast(ctx,"熄屏暂不可用，已保留屏保")}
            }
            if(ScreenSaverRuntime.offAt>0L) {
                if(ScreenPower.screenOn(ctx))ScreenSaverRuntime.wake()
                else if(config.saverStandbyMinutes>0 && now-ScreenSaverRuntime.offAt>=config.saverStandbyMinutes*60_000L) {
                    ScreenPower.standby();ScreenSaverRuntime.wake()
                }
            }
        }
    }
    var displayed by remember {mutableStateOf(active)}
    var ready by remember {mutableStateOf(false)}
    val opacity=remember {androidx.compose.animation.core.Animatable(0f)}
    LaunchedEffect(active) {
        if(active) {ready=config.saverMode==0;displayed=true;opacity.snapTo(0f)}
        else if(displayed) {opacity.animateTo(0f,androidx.compose.animation.core.tween(450));displayed=false;onExit()}
    }
    LaunchedEffect(active,ready) {if(active && ready)opacity.animateTo(1f,androidx.compose.animation.core.tween(600))}
    if(!displayed)return
    if(dark) {Box(Modifier.fillMaxSize().background(Color.Black));return}
    Box(Modifier.fillMaxSize().graphicsLayer {alpha=opacity.value}) {
        if(!active) frame?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
        else when(config.saverMode) {
            1->SaverPhotos(config) {ready=true}
            2->SaverVideo(config,frame?.asImageBitmap()) {ready=true}
            else->FlowingGradient(config)
        }
    }
}

@Composable
private fun FlowingGradient(config:LauncherConfig) {
    var phase by remember {mutableFloatStateOf(kotlin.random.Random.nextFloat()*12f)}
    LaunchedEffect(config.saverFlow) {if(config.saverFlow)while(true){delay(80);phase+=.002f}}
    val colors=remember(config.saverGradient,config.saverGradientColors) {
        if(config.saverGradient<0)config.saverGradientColors.mapNotNull {runCatching {Color(android.graphics.Color.parseColor("#"+it.removePrefix("#")))}.getOrNull()}.takeIf {it.size>=2} ?: WALLPAPERS[1].colors
        else WALLPAPERS[config.saverGradient.coerceIn(WALLPAPERS.indices)].colors
    }
    Canvas(Modifier.fillMaxSize()) {
        if(!config.saverFlow)drawRect(Brush.linearGradient(colors))
        else {
            val hue=(phase*30f)%360f
            val c1=Color.hsv(hue,.58f,.53f);val c2=Color.hsv((hue+100f)%360f,.65f,.64f);val c3=Color.hsv((hue+235f)%360f,.6f,.55f)
            drawRect(c1)
            drawRect(Brush.radialGradient(listOf(c2,Color.Transparent),Offset(size.width*(.5f+.4f*sin(phase)),size.height*(.5f+.4f*cos(phase*.73f))),size.width*.85f))
            drawRect(Brush.radialGradient(listOf(c3,Color.Transparent),Offset(size.width*(.5f+.4f*cos(phase*.81f)),size.height*(.5f+.4f*sin(phase*.69f))),size.width*.75f))
        }
    }
}

@Composable
private fun SaverPhotos(config:LauncherConfig,onReady:()->Unit) {
    val ctx=LocalContext.current
    var chosen by remember(config.saverPhotos) {mutableStateOf(config.saverPhotos.firstOrNull() ?: "gradient:aurora")}
    var image by remember {mutableStateOf<ImageBitmap?>(null)}
    LaunchedEffect(chosen) {
        if(image==null)withContext(Dispatchers.IO) {AppleWallpapers.decode(ctx,"gradient:aurora")}?.let {image=it.asImageBitmap()}
        withContext(Dispatchers.IO) {runCatching {AppleWallpapers.decode(ctx,chosen)}.getOrNull()}?.let {image=it.asImageBitmap()}
    }
    LaunchedEffect(config.saverPhotoRandom,config.saverPhotos) {
        val pool=config.saverPhotos.takeIf {it.size>1} ?: listOf("gradient:aurora","gradient:ocean","gradient:dusk")
        if(config.saverPhotoRandom)while(true){delay(60_000);chosen=pool.filter {it!=chosen}.random()}
    }
    LaunchedEffect(image) {if(image!=null)onReady()}
    androidx.compose.animation.Crossfade(image,animationSpec=androidx.compose.animation.core.tween(420),label="saver-photo") {frame->
        frame?.let {Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
    }
}

@Composable
private fun SaverVideo(config:LauncherConfig,initial:ImageBitmap?,onReady:()->Unit) {
    val ctx=LocalContext.current
    var uri by remember {mutableStateOf<String?>(null)}
    var choices by remember {mutableStateOf(emptyList<String>())}
    var failed by remember {mutableStateOf(false)}
    LaunchedEffect(config.saverVideoSource,config.saverVideoUri) {
        choices=withContext(Dispatchers.IO) {com.conreo.couchytv.data.BuiltinAerials.load(ctx,config.saverVideoSource)}
        uri=config.saverVideoUri.takeIf {it in choices} ?: config.pinnedAerial.takeIf {it in choices} ?: choices.firstOrNull()
    }
    if(failed) {SaverPhotos(config.copy(saverPhotos=emptyList(),saverPhotoRandom=false),onReady);return}
    uri?.let {u->ManagedVideoWallpaper(u,1f,!config.saverVideoRandom,Brush.linearGradient(listOf(Color.Transparent,Color.Transparent)),initial,
        onReady=onReady,onEnded={if(config.saverVideoRandom)uri=choices.filter {it!=u}.randomOrNull() ?: u},
        onError={failed=true},rememberAsDesktop=false)}
}
