// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.media.tv.TvView
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.conreo.couchytv.Actions
import com.conreo.couchytv.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InputSourceDialog(config: LauncherConfig, store: ConfigStore, onDismiss: () -> Unit) {
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    val inputs=remember { SourceController.list(ctx) }
    val focus=remember { FocusRequester() }
    var selected by remember { mutableStateOf(inputs.firstOrNull()) }
    var previewView by remember { mutableStateOf<TvView?>(null) }
    val frame=LocalGlassFrame.current
    val root=LocalGlassRoot.current
    LaunchedEffect(Unit) { focus.requestWhenReady() }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val view=androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) { (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let {
            it.setLayout(-1,-1);it.setDimAmount(0f);it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        };onDispose {} }
        CompositionLocalProvider(LocalGlassFrame provides frame,LocalGlassRoot provides root) {
        ScaledUi(uiScaleFactor(config.uiScale,androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
        Row(Modifier.fillMaxSize().padding(top=110.dp,end=42.dp,start=42.dp,bottom=30.dp),
            horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.Top) {
            if(config.inputPreview && selected!=null) {
                GlassSurface(Modifier.width(310.dp)) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        InputName(selected!!,config,19.sp,Color.White)
                        key(selected!!.id) { InputPreview(selected!!) { previewView=it } }
                        Text("实时预览 · 静音",fontSize=11.sp,color=Color.White.copy(alpha=.6f))
                    }
                }
                Spacer(Modifier.width(18.dp))
            }
            GlassSurface(Modifier.width(330.dp).heightIn(max=500.dp)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("信号源",Modifier.padding(10.dp),fontSize=19.sp,color=Color.White)
                    if(inputs.isEmpty()) Text("未找到信号源",color=Color.White)
                    val list=androidx.compose.foundation.lazy.rememberLazyListState()
                    val loop=rememberLazyMenuLoop(inputs.size,list)
                    LazyColumn(state=list,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        itemsIndexed(inputs) { i,input ->
                            ListItem(selected=false,
                                modifier=loop.item(i).then(if(i==0) Modifier.focusRequester(focus) else Modifier).onFocusChanged { if(it.isFocused) selected=input },
                                headlineContent={InputName(input,config)},
                                leadingContent={Icon(if(input.name.startsWith("HDMI")) AppIcons.Input else AppIcons.Television,null)},
                                trailingContent={if(config.lastInput==input.id) Icon(AppIcons.Check,null,Modifier.size(18.dp))},
                                colors=ListItemDefaults.colors(containerColor=Color.Transparent,focusedContainerColor=Color(0xEEF5F6F8),focusedContentColor=Color(0xFF16191F)),
                                onClick={
                                    scope.launch {
                                        store.update { it.copy(lastInput=input.id) }
                                        previewView?.reset()
                                        runCatching { SourceController.open(ctx,input) }.onFailure { Actions.toast(ctx,"无法打开 ${input.name}") }
                                        onDismiss()
                                    }
                                })
                        }
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun InputPreview(input: InputSource, onReady:(TvView)->Unit) {
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context=LocalContext.current
    val manager=remember {context.getSystemService(TvInputManager::class.java)}
    var inputState by remember(input.id) {mutableIntStateOf(runCatching {manager.getInputState(input.id)}.getOrDefault(TvInputManager.INPUT_STATE_DISCONNECTED))}
    var failed by remember {mutableStateOf(false)}
    DisposableEffect(manager,input.id) {
        val callback=object:TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(id:String,state:Int) {if(id==input.id) {inputState=state;failed=false}}
        }
        manager.registerCallback(callback,android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose {manager.unregisterCallback(callback)}
    }
    val connected=input.info.type!=android.media.tv.TvInputInfo.TYPE_HDMI || inputState==TvInputManager.INPUT_STATE_CONNECTED
    var tv by remember { mutableStateOf<TvView?>(null) }
    var status by remember { mutableStateOf(if(input.info.isPassthroughInput) "正在连接…" else "按确定进入电视，选择频道") }
    var available by remember { mutableStateOf(false) }
    val currentId by rememberUpdatedState(input.id)
    DisposableEffect(owner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,e -> if(e==androidx.lifecycle.Lifecycle.Event.ON_STOP) tv?.reset() }
        owner.lifecycle.addObserver(observer)
        onDispose { tv?.reset();owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(input.id) {failed=false;available=false}
    LaunchedEffect(input.id,connected,failed) {
        if(!connected || failed) {tv?.reset();available=false;if(!connected) status="设备未开机或未连接"}
    }
    LaunchedEffect(input.id,tv,connected,failed) {
        val v=tv ?: return@LaunchedEffect
        v.reset();available=false
        if(!connected || failed) return@LaunchedEffect
        status="正在连接…"
        if(!input.info.isPassthroughInput) { status="按确定进入电视，选择频道";return@LaunchedEffect }
        delay(450)
        runCatching {
            v.setStreamVolume(0f)
            v.tune(input.id,TvContract.buildChannelUriForPassthroughInput(input.id))
        }.onFailure { status="系统暂不支持窗口预览";android.util.Log.w("CouchyPreview","tune failed",it) }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black),contentAlignment=Alignment.Center) {
        // Tuner/channel entries have no passthrough URI. An idle native TvView
        // would still punch a surface hole through the dialog's glass layer.
        if(connected && !failed && input.info.isPassthroughInput) AndroidView(modifier=Modifier.fillMaxSize(),factory={ context -> object:TvView(context) {
            override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
                super.onLayout(changed,left,top,right,bottom)
                // Some TV inputs request a full-display surface even for a preview.
                // Keep their SurfaceView inside the preview bounds.
                for(i in 0 until childCount) getChildAt(i).layout(0,0,width,height)
            }
        }.apply {
            isFocusable=false;isFocusableInTouchMode=false
            // Keep the bounded video surface; Xiaomi's vendor overlay otherwise
            // adds a full-screen black UI above the launcher.
            setZOrderMediaOverlay(true)
            setCallback(object:TvView.TvInputCallback() {
                override fun onVideoAvailable(id:String) {if(id!=currentId) return;available=true;status="";android.util.Log.i("CouchyPreview","available $id")}
                override fun onVideoUnavailable(id:String,reason:Int) {if(id!=currentId) return;available=false;status=when(reason) {
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING -> "正在连接…"
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL -> {failed=true;"信号较弱"}
                    TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> "正在缓冲…"
                    else -> "暂无视频信号"
                };android.util.Log.i("CouchyPreview","unavailable $id reason=$reason")}
                override fun onConnectionFailed(id:String) {if(id!=currentId) return;available=false;failed=true;status="系统未允许预览此输入";android.util.Log.w("CouchyPreview","connection failed $id")}
                override fun onDisconnected(id:String) {if(id!=currentId) return;available=false;status="信号已断开"}
            });tv=this;onReady(this)
        } })
        if(!available) Text(status,fontSize=13.sp,color=Color.White.copy(alpha=.8f),modifier=Modifier.padding(16.dp))
    }
}

@Composable
fun InputName(input:InputSource,config:LauncherConfig,fontSize:androidx.compose.ui.unit.TextUnit=20.sp,color:Color=Color.Unspecified) {
    val prefix=config.inputAliases[input.name]?.trim().orEmpty()
    Row(Modifier.fillMaxWidth()) {
        if(prefix.isNotEmpty()) Text(prefix,Modifier.weight(1f,fill=false),fontSize=fontSize,color=color,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text((if(prefix.isNotEmpty()) "-" else "")+input.name,fontSize=fontSize,color=color,maxLines=1)
    }
}
