// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.graphics.Matrix
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

/** One SDR texture pipeline for the still and moving picture, one decoder, restart at zero.
 * No position polling, periodic screenshots, menu sampling, or background decoder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ManagedVideoWallpaper(uri:String,speed:Float,loop:Boolean,coverBrush:Brush,initialCover:ImageBitmap?,
    onReady:()->Unit,onEnded:()->Unit,onError:()->Unit,rememberAsDesktop:Boolean=true) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val owner=LocalLifecycleOwner.current
    val fixedClip by rememberUpdatedState(loop)
    val ready by rememberUpdatedState(onReady);val ended by rememberUpdatedState(onEnded);val error by rememberUpdatedState(onError)
    var texture by remember {mutableStateOf<TextureView?>(null)}
    var player by remember {mutableStateOf<ExoPlayer?>(null)}
    var foreground by remember {mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var cover by remember {mutableStateOf(initialCover)}
    var covered by remember {mutableStateOf(true)}
    var rendered by remember {mutableIntStateOf(0)}
    var loadGeneration by remember {mutableIntStateOf(0)}
    var frameGeneration by remember {mutableIntStateOf(-1)}
    var currentUri by remember {mutableStateOf("")}
    var preparedFirst by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    val opacity=remember {Animatable(1f)}
    val factory by produceState<androidx.media3.datasource.DataSource.Factory?>(null) {
        value=withContext(Dispatchers.IO) {WallpaperMedia.factory(context.applicationContext)}
    }
    fun setCovered() {covered=true;WallpaperPlaybackState.covered=true}
    DisposableEffect(owner,factory) {
        val source=factory ?: return@DisposableEffect onDispose {}
        fun build():ExoPlayer {
            val selector=androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
                setParameters(buildUponParameters().setMaxVideoSize(1920,1080).setMaxVideoFrameRate(30).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true))
            }
            return ExoPlayer.Builder(context).setTrackSelector(selector)
                .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(source))
                .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(3000,12000,500,1500).setPrioritizeTimeOverSizeThresholds(true).build())
                .build().apply {volume=0f;playWhenReady=false}
        }
        fun release() {
            player?.let {p->p.videoDecoderCounters?.also {it.ensureUpdated();WallpaperPlaybackState.droppedFrames=it.droppedBufferCount;WallpaperPlaybackState.renderedFrames=it.renderedOutputBufferCount};p.release()}
            player=null;setCovered()
        }
        val hook=com.conreo.couchytv.WallpaperLaunchGate.install(snapshot={texture?.bitmap}) {leaving->
            if(leaving) {player?.pause();setCovered()}
            else if(frameGeneration==loadGeneration && frameGeneration>=0) {
                covered=false;WallpaperPlaybackState.covered=false;scope.launch {opacity.snapTo(0f)};player?.play()
            }
        }
        val observer=LifecycleEventObserver {_,event->when(event) {
            Lifecycle.Event.ON_START->{if(player==null)player=build()}
            Lifecycle.Event.ON_RESUME->{foreground=true;if(!covered)player?.play()}
            Lifecycle.Event.ON_PAUSE->{foreground=false;player?.pause();setCovered()}
            Lifecycle.Event.ON_STOP->release()
            else->{}
        }}
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))player=build()
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);hook();release()}
    }
    // A menu recomposition must not disconnect and recreate the decoder's surface.
    DisposableEffect(player,texture) {
        val p=player;val v=texture
        if(p!=null && v!=null) {p.setVideoTextureView(v);WallpaperPlaybackState.surfaceBinds++}
        onDispose {if(p!=null && v!=null)runCatching {p.clearVideoTextureView(v)}}
    }
    LaunchedEffect(player,speed,loop) {player?.setPlaybackSpeed(speed);player?.repeatMode=Player.REPEAT_MODE_ONE;WallpaperPlaybackState.repeating=loop}
    DisposableEffect(player) {
        val p=player ?: return@DisposableEffect onDispose {}
        val inspect:()->android.os.Bundle={
            p.videoDecoderCounters?.ensureUpdated()
            android.os.Bundle().apply {
                putLong("position_ms",p.currentPosition);putLong("buffered_ms",p.bufferedPosition)
                putBoolean("playing",p.isPlaying);putInt("player_state",p.playbackState)
                putInt("dropped_frames",p.videoDecoderCounters?.droppedBufferCount ?: 0)
                putInt("rendered_frames",p.videoDecoderCounters?.renderedOutputBufferCount ?: 0)
            }
        }
        WallpaperPlaybackState.inspect=inspect
        val listener=object:Player.Listener {
            override fun onRenderedFirstFrame() {if(player===p) {frameGeneration=loadGeneration;rendered++}}
            override fun onPlaybackStateChanged(state:Int) {if(state==Player.STATE_ENDED)ended()}
            override fun onMediaItemTransition(item:MediaItem?,reason:Int) {
                if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && !fixedClip)ended()
            }
            override fun onPlayerError(e:PlaybackException) {android.util.Log.w("CouchyVideo","media failed: ${e.errorCodeName}");setCovered();error()}
            override fun onVideoSizeChanged(size:VideoSize) {
                texture?.let {v->if(v.width>0 && v.height>0 && size.width>0 && size.height>0) {
                    val ratio=(size.width*size.pixelWidthHeightRatio/size.height)/(v.width.toFloat()/v.height)
                    v.setTransform(Matrix().apply {setScale(maxOf(1f,ratio),maxOf(1f,1f/ratio),v.width/2f,v.height/2f)})
                }}
            }
        };p.addListener(listener);onDispose {if(WallpaperPlaybackState.inspect===inspect)WallpaperPlaybackState.inspect=null;p.removeListener(listener)}
    }
    LaunchedEffect(player,uri) {
        val p=player ?: return@LaunchedEffect
        // One transient frame only at the actual hand-off; old video kept playing during prefetch.
        if(currentUri.isNotEmpty() && currentUri!=uri && !covered) texture?.bitmap?.let {cover=it.asImageBitmap()}
        setCovered();opacity.snapTo(1f);loadGeneration++;frameGeneration=-1
        val first=withContext(Dispatchers.IO) {WallpaperMedia.first(context,uri)}
        preparedFirst=first
        if(currentUri.isEmpty() || currentUri==uri) first?.let {cover=it.asImageBitmap();if(rememberAsDesktop)VideoFrameCache.use(it)}
        currentUri=uri;WallpaperPlaybackState.uri=uri;WallpaperPlaybackState.positionMs=0
        p.playWhenReady=false;p.setMediaItem(MediaItem.fromUri(uri));p.prepare()
        delay(20_000)
        if(frameGeneration!=loadGeneration && foreground && player===p)error()
    }
    LaunchedEffect(rendered,foreground,player,uri) {
        val p=player ?: return@LaunchedEffect
        if(!foreground || frameGeneration!=loadGeneration || frameGeneration<0)return@LaunchedEffect
        // Capture the paused first frame from the SAME texture used for live playback.
        // Both images therefore pass through the same SDR composition and crop.
        repeat(2) {withFrameNanos {}}
        delay(40)
        if(player!==p || frameGeneration!=loadGeneration)return@LaunchedEffect
        val saved=preparedFirst
        val bitmap=saved ?: texture?.bitmap ?: return@LaunchedEffect
        preparedFirst=bitmap
        if(rememberAsDesktop)VideoFrameCache.use(bitmap)
        withContext(Dispatchers.IO) {runCatching {WallpaperMedia.saveFirst(context,uri,bitmap,rememberAsDesktop)}.onFailure {android.util.Log.w("CouchyVideo","first frame cache unavailable",it)}}
        covered=false
        opacity.animateTo(0f,tween(420))
        cover=bitmap.asImageBitmap();WallpaperPlaybackState.covered=false
        p.play();ready()
        android.util.Log.i("CouchyVideo","first frame ready; restart=0; texture SDR; cache enabled")
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx->TextureView(ctx).also {texture=it}})
        Box(Modifier.fillMaxSize().graphicsLayer {alpha=if(covered)1f else opacity.value}) {
            if(cover!=null)Image(cover!!,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,filterQuality=FilterQuality.High)
            else Box(Modifier.fillMaxSize().background(coverBrush))
        }
    }
}
