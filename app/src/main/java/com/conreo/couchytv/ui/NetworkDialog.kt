// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.conreo.couchytv.Actions
import com.conreo.couchytv.data.LauncherConfig
import com.conreo.couchytv.data.NetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class NetworkDetails(val internet:Boolean=false,val address:String="",val name:String="",val loaded:Boolean=false)

/** Desktop quick panel; connection/password changes are explicitly opened in TV settings. */
@Composable
fun NetworkDialog(config:LauncherConfig,net:NetStatus,onDismiss:()->Unit) {
    val ctx=LocalContext.current
    val focus=remember {FocusRequester()}
    val frame=LocalGlassFrame.current
    val root=LocalGlassRoot.current
    val details by produceState(NetworkDetails(),net) {
        value=withContext(Dispatchers.IO) {
            runCatching {
                val cm=ctx.getSystemService(ConnectivityManager::class.java)
                @Suppress("DEPRECATION")
                val network=cm.allNetworks.firstOrNull { n ->
                    val caps=cm.getNetworkCapabilities(n)
                    caps?.hasTransport(if(net.ethernet) NetworkCapabilities.TRANSPORT_ETHERNET else NetworkCapabilities.TRANSPORT_WIFI)==true
                }
                val caps=network?.let {cm.getNetworkCapabilities(it)}
                @Suppress("DEPRECATION", "MissingPermission")
                val wifi=runCatching {ctx.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)?.connectionInfo}.getOrNull()
                val ssid=wifi?.ssid?.removeSurrounding("\"")?.takeUnless {it=="<unknown ssid>"}.orEmpty()
                val ip=network?.let {cm.getLinkProperties(it)}?.linkAddresses?.firstOrNull {it.address is Inet4Address}?.address?.hostAddress.orEmpty()
                val internet=cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
                NetworkDetails(internet,ip,ssid,true)
            }.getOrDefault(NetworkDetails(loaded=true))
        }
    }
    LaunchedEffect(Unit) {focus.requestWhenReady()}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val view=androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {(view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
            setLayout(-1,-1);setDimAmount(0f)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        };onDispose {}}
        CompositionLocalProvider(LocalGlassFrame provides frame,LocalGlassRoot provides root) {
        ScaledUi(uiScaleFactor(config.uiScale,androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
            Box(Modifier.fillMaxSize().padding(top=110.dp,end=42.dp),contentAlignment=Alignment.TopEnd) {
                GlassSurface(Modifier.width(350.dp)) {
                    Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("网络",fontSize=21.sp,color=Color.White)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Icon(if(net.ethernet) AppIcons.Ethernet else if(net.wifi) AppIcons.Wifi else AppIcons.WifiOff,null,Modifier.size(30.dp))
                            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(if(net.ethernet) "有线网络" else if(net.wifi) details.name.ifEmpty {"Wi-Fi"} else "未连接网络",fontSize=19.sp)
                                Text(if(!net.connected) "请连接有线或无线网络" else if(!details.loaded) "正在检查网络…" else if(details.internet) "已连接 · 可访问互联网" else "已连接 · 互联网连接受限",
                                    fontSize=12.sp,color=Color.White.copy(alpha=.7f))
                            }
                        }
                        if(details.address.isNotEmpty()) Text("IP 地址  ${details.address}",fontSize=14.sp,color=Color.White.copy(alpha=.8f))
                        ListItem(selected=false,onClick={onDismiss();Actions.openNetworkSettings(ctx)},
                            modifier=Modifier.focusRequester(focus),
                            headlineContent={Text("打开电视网络设置",fontSize=16.sp)},
                            leadingContent={Icon(AppIcons.Television,null)},
                            colors=ListItemDefaults.colors(containerColor=Color.Transparent,focusedContainerColor=Color(0xEEF5F6F8),focusedContentColor=Color(0xFF16191F)))
                        Text("连接 Wi-Fi 和修改密码，请进入电视网络设置",fontSize=11.sp,color=Color.White.copy(alpha=.55f))
                    }
                }
            }
        }
        }
    }
}
