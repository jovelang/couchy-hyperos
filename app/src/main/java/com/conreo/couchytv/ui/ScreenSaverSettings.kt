// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.conreo.couchytv.Actions
import com.conreo.couchytv.data.*
import kotlinx.coroutines.*

private val durations=listOf(0,1,2,5,10,15,30,60,120)
private fun durationNames(never:String)=durations.map {if(it==0)never else "$it 分钟"}

@Composable
fun ScreenSaverSettings(config:LauncherConfig,store:ConfigStore,onBack:()->Unit) {
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var page by remember {mutableIntStateOf(0)}
    var editor by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf("")}
    fun update(f:(LauncherConfig)->LauncherConfig) {scope.launch {store.update(f)}}
    val own=config.saverEnabled && config.saverOwner==1
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris->
        if(uris.isNotEmpty()) {
            uris.forEach {runCatching {ctx.contentResolver.takePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
            update {it.copy(saverPhotos=uris.map {u->u.toString()})}
        }
    }
    androidx.activity.compose.BackHandler(enabled=page!=0) {page=if(page>=3)1 else 0}
    if(page==3) {
        val entries by produceState<List<AppleWallpapers.Entry>>(emptyList()) {
            value=withContext(Dispatchers.IO) {AppleWallpapers.entries(ctx)}
        }
        val list=androidx.compose.foundation.lazy.rememberLazyListState()
        val loop=rememberLazyMenuLoop(entries.size,list,1+if(loading.isNotEmpty())1 else 0)
        val first=remember {androidx.compose.ui.focus.FocusRequester()}
        LaunchedEffect(entries.isNotEmpty()) {if(entries.isNotEmpty())first.requestWhenReady()}
        LazyColumn(Modifier.fillMaxSize().padding(20.dp),state=list) {
            item {Text("苹果图片库",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(8.dp))}
            if(loading.isNotEmpty())item {Text("正在下载 $loading",modifier=Modifier.padding(8.dp))}
            items(entries.size,key={entries[it].url}) {i->val e=entries[i]
                SettingsItem(onClick={if(loading.isEmpty()) {loading=e.name;scope.launch {
                    runCatching {withContext(Dispatchers.IO) {AppleWallpapers.obtain(ctx,e)}}
                        .onSuccess {update {it.copy(saverPhotos=listOf(e.url))};page=1}
                        .onFailure {Actions.toast(ctx,"下载失败，请稍后重试")}
                    loading=""
                }}},headlineContent={Text(e.name)},modifier=loop.item(i).then(if(i==0)Modifier.focusRequester(first)else Modifier))
            }
        }
        return
    }
    if(page==4) {
        val entries by produceState<List<Pair<String,String>>>(emptyList(),config.saverVideoSource) {
            value=withContext(Dispatchers.IO) {BuiltinAerials.named(ctx,config.saverVideoSource)}
        }
        val list=androidx.compose.foundation.lazy.rememberLazyListState()
        val loop=rememberLazyMenuLoop(entries.size,list,1)
        val first=remember {androidx.compose.ui.focus.FocusRequester()}
        LaunchedEffect(entries.isNotEmpty()) {if(entries.isNotEmpty())first.requestWhenReady()}
        LazyColumn(Modifier.fillMaxSize().padding(20.dp),state=list) {
            item {Text("选择视频",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(8.dp))}
            if(entries.isEmpty())item {Text("正在读取视频库…",modifier=Modifier.padding(8.dp))}
            items(entries.size,key={entries[it].second}) {i->val e=entries[i]
                SettingsItem(onClick={update {it.copy(saverVideoUri=e.second)};page=1},headlineContent={Text(e.first)},
                    trailingContent={if(config.saverVideoUri==e.second)Icon(AppIcons.Check,null)},
                    modifier=loop.item(i).then(if(i==0)Modifier.focusRequester(first)else Modifier))
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(when(page){1->"屏保画面";2->"熄屏与休眠";else->"屏保与休眠"},style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(8.dp))
        when(page) {
            0->{
                ToggleSetting("使用桌面屏保",own,{v->update {it.copy(saverEnabled=v,saverOwner=if(v)1 else 0)}},
                    Modifier.focusRequester(initialFocus()),description=if(own)"播放节目时不进入"else"关闭时使用电视自带屏保")
                SettingsLink("屏保画面",listOf("渐变","图片","视频")[config.saverMode.coerceIn(0,2)],{page=1})
                ChoiceSetting("无操作多久后进入",durationNames("不自动进入"),durations.indexOf(config.saverAfterMinutes).coerceAtLeast(0),{v->update {it.copy(saverAfterMinutes=durations[v])}},enabled=own)
                SettingsLink("熄屏与休眠",onClick={page=2})
                SectionLabel("预览")
                SettingsItem(onClick={ScreenSaverRuntime.preview();onBack()},headlineContent={Text("预览屏保")},supportingContent={Text("按任意方向键或确定键退出")})
                if(own) Text("桌面按无操作时间计时；应用内由电视系统判断是否闲置。",modifier=Modifier.padding(8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            1->{
                ChoiceSetting("画面类型",listOf("渐变","图片","视频"),config.saverMode.coerceIn(0,2),{v->update {it.copy(saverMode=v)}},Modifier.focusRequester(initialFocus()))
                SectionLabel("画面设置")
                when(config.saverMode) {
                    0->{
                        ToggleSetting("流动渐变",config.saverFlow,{v->update {it.copy(saverFlow=v)}})
                        if(!config.saverFlow) {
                            ChoiceSetting("渐变颜色",listOf("深夜","极光","日落","深海","炭黑","自定义"),if(config.saverGradient<0)5 else config.saverGradient.coerceIn(0,4),{v->update {it.copy(saverGradient=if(v==5)-1 else v)}})
                            if(config.saverGradient<0)SettingsLink("自定义颜色",onClick={editor=true})
                        }
                    }
                    1->{
                        SettingsLink("苹果图片库",onClick={page=3})
                        SettingsLink("选择自己的图片",if(config.saverPhotos.isEmpty())"未选择"else"${config.saverPhotos.size} 张",{
                            runCatching {picker.launch(arrayOf("image/*"))}.onFailure {Actions.toast(ctx,"没有可用的文件选择器")}
                        })
                        ToggleSetting("随机换图片",config.saverPhotoRandom,{v->update {it.copy(saverPhotoRandom=v)}},description="关闭后保持当前图片")
                    }
                    else->{
                        ToggleSetting("随机换视频",config.saverVideoRandom,{v->update {it.copy(saverVideoRandom=v)}})
                        ChoiceSetting("视频库",listOf("全部","Apple","Amazon","社区一","社区二"),config.saverVideoSource.coerceIn(0,4),{v->update {it.copy(saverVideoSource=v,saverVideoUri="")}})
                        SettingsLink("固定视频",if(config.saverVideoUri.isEmpty())"跟随桌面"else"已选择",{page=4})
                        if(config.saverVideoUri.isNotEmpty())SettingsItem(onClick={update {it.copy(saverVideoUri="")}},headlineContent={Text("使用当前桌面视频")})
                    }
                }
                SectionLabel("预览")
                SettingsItem(onClick={ScreenSaverRuntime.preview();onBack()},headlineContent={Text("预览这个画面")})
            }
            2->{
                ChoiceSetting("屏保多久后熄屏",durationNames("保持亮屏"),durations.indexOf(config.saverScreenOffMinutes).coerceAtLeast(0),{v->update {it.copy(saverScreenOffMinutes=durations[v])}},Modifier.focusRequester(initialFocus()),enabled=own)
                ChoiceSetting("熄屏多久后休眠",durationNames("不自动休眠"),durations.indexOf(config.saverStandbyMinutes).coerceAtLeast(0),{v->update {it.copy(saverStandbyMinutes=durations[v])}},enabled=own && config.saverScreenOffMinutes>0)
                SectionLabel("唤醒方式")
                Text("熄屏：按方向键或确定键亮屏。\n休眠：按电源键唤醒。",modifier=Modifier.padding(8.dp))
                Text("休眠由电视系统执行，部分机型会清理后台应用。",modifier=Modifier.padding(8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(editor)ValueEditor("自定义渐变",config.saverGradientColors.joinToString(","),"输入 2–4 个颜色，例如 13547A,2B825B",onSave={value->
        val colors=value.split(',', '，',' ').filter {it.isNotBlank()}.map {it.trim().removePrefix("#").uppercase()}
        if(colors.size in 2..4 && colors.all {it.matches(Regex("[0-9A-F]{6}"))}) {update {it.copy(saverGradient=-1,saverGradientColors=colors)};editor=false}
        else Actions.toast(ctx,"请输入 2–4 个六位颜色值")
    },onDismiss={editor=false})
}
