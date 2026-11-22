// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.conreo.couchytv.Actions
import com.conreo.couchytv.data.LauncherConfig
import kotlinx.coroutines.*
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun RemoteControlPage(config:LauncherConfig,onBack:()->Unit) {
    val ctx=LocalContext.current
    val ready=config.remoteControlUrl.isNotBlank() && config.remoteControlCode.isNotBlank()
    var online by remember {mutableStateOf<Boolean?>(null)}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(config.remoteControlLanUrl,owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while(ready) {
                online=withContext(Dispatchers.IO) {runCatching {
                    val connection=java.net.URL(config.remoteControlLanUrl.trimEnd('/')+"/health").openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout=1500;connection.readTimeout=1500
                    try {connection.responseCode==200}finally {connection.disconnect()}
                }.getOrDefault(false)}
                delay(10_000)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("远程控制",style=MaterialTheme.typography.titleLarge)
        Text("用同一网络里的手机或电脑控制电视",fontSize=15.sp)
        if(ready) {
            Text(when(online){true->"网页服务已连接";false->"网页服务暂时离线";null->"正在检查网页服务…"},color=MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("访问地址")
            Text(config.remoteControlUrl,fontSize=18.sp)
            Text(config.remoteControlLanUrl,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("首次连接码")
            Text(config.remoteControlCode.chunked(4).joinToString("  "),fontSize=34.sp,fontFamily=FontFamily.Monospace)
            Text("在网页输入连接码，之后会记住这台设备。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("支持的功能")
            Text("开关机与运行状态 · 遥控与截图\n桌面和电视设置 · 文件传输与 APK 安装",fontSize=15.sp)
            Text("网页服务运行在同一网络的电脑或服务器上；开机需要配置米家通道。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsItem(onClick={runCatching {
                val uri=Uri.parse(config.remoteControlUrl);require(uri.scheme in listOf("http","https"))
                ctx.startActivity(Intent(Intent.ACTION_VIEW,uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {Actions.toast(ctx,"请用手机或电脑访问上方地址")}},headlineContent={Text("在电视浏览器打开")},leadingContent={Icon(AppIcons.Remote,null)})
        } else Text("尚未配置局域网网页服务。",fontSize=16.sp)
        SettingsItem(onClick=onBack,modifier=Modifier.focusRequester(initialFocus()),headlineContent={Text("返回桌面设置")},leadingContent={Icon(AppIcons.Back,null)})
    }
}
