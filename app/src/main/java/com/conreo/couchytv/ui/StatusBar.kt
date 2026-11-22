// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.conreo.couchytv.data.NetStatus
import java.util.Calendar

@Composable
fun StatusBar(net: NetStatus,time:String,date:String,lunar:String,showGreeting:Boolean,
    nickname:String,weather:com.conreo.couchytv.data.WeatherReading?,weatherLoading:Boolean,showWeather:Boolean,onCalendarClick:()->Unit,menuFocus:FocusRequester,appFocus:FocusRequester?,
    onInputsClick:()->Unit,onInputsHold:()->Unit,onNextClick:()->Unit,switching:Boolean,inputsOpen:Boolean,settingsOpen:Boolean,
    showVpn:Boolean,glass:Boolean,onVpnClick:()->Unit,onNetworkClick:()->Unit,networkOpen:Boolean,
    onSettingsClick:()->Unit,onSettingsHold:()->Unit) {
    val hour=remember(time) {Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}
    val greeting=when(hour) {in 5..10->"早上好";in 11..13->"中午好";in 14..17->"下午好";in 18..23->"晚上好";else->"夜深了"}
    var clockFocused by remember {mutableStateOf(false)}
    val clockFocus=remember {FocusRequester()}
    Row(Modifier.fillMaxWidth().padding(horizontal=42.dp,vertical=14.dp),
        horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            if(showGreeting) Text((nickname.trim().takeIf {it.isNotEmpty()}?.plus("，") ?: "")+greeting,
                modifier=Modifier.padding(start=6.dp),fontSize=12.sp,letterSpacing=1.sp,maxLines=1,
                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=Color.White.copy(alpha=.7f))
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(onClick=onCalendarClick,modifier=Modifier.focusRequester(clockFocus)
                    .focusProperties {right=menuFocus;down=appFocus ?: FocusRequester.Default}.onFocusChanged {clockFocused=it.isFocused},
                    shape=ClickableSurfaceDefaults.shape(SmoothCornerShape(14.dp)),
                    scale=ClickableSurfaceDefaults.scale(focusedScale=1f),
                    colors=ClickableSurfaceDefaults.colors(containerColor=Color.Transparent,focusedContainerColor=Color.Transparent)) {
                    Box(Modifier.border(1.dp,if(clockFocused) Color.White.copy(alpha=.8f) else Color.Transparent,SmoothCornerShape(14.dp))) {
                        Text(time,modifier=Modifier.padding(horizontal=6.dp,vertical=2.dp)
                            .then(Modifier.semantics {contentDescription="时间 $time，打开日历"}),
                            fontSize=43.sp,fontWeight=FontWeight.Light,color=Color.White,
                            style=MaterialTheme.typography.displaySmall.copy(fontFeatureSettings="tnum"))
                    }
                }
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    if(date.isNotEmpty()) Text(date,fontSize=15.sp,color=Color.White.copy(alpha=.85f))
                    if(lunar.isNotEmpty() || showWeather) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        if(lunar.isNotEmpty()) Text(lunar,fontSize=11.sp,color=Color.White.copy(alpha=.65f))
                        if(showWeather) {
                            Icon(AppIcons.Weather,null,Modifier.size(17.dp),tint=Color.White.copy(alpha=.8f))
                            val summary=weather?.let {"${kotlin.math.round(it.temperature).toInt()}°  ${it.description}"} ?: if(weatherLoading) "天气加载中" else "天气暂不可用"
                            Text(summary,fontSize=11.sp,color=Color.White.copy(alpha=.75f),maxLines=1)
                        }
                    }
                }
            }
        }
        var groupFocused by remember {mutableStateOf(false)}
        val labelsVisible=groupFocused || inputsOpen || settingsOpen || networkOpen
        Row(Modifier.onFocusChanged {groupFocused=it.hasFocus}.focusGroup(),
            horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
            StatusIcon(AppIcons.Cycle,if(switching) "正在切换中" else "切换背景",onNextClick,showLabel=labelsVisible || switching,spinning=switching,
                modifier=Modifier.focusRequester(menuFocus).focusProperties {left=clockFocus;down=appFocus ?: FocusRequester.Default})
            val toApps=Modifier.focusProperties {down=appFocus ?: FocusRequester.Default}
            StatusIcon(AppIcons.Input,"信号源",onInputsClick,onInputsHold,inputsOpen,labelsVisible,modifier=toApps)
            if(showVpn) StatusIcon(AppIcons.Vpn,"VPN",onVpnClick,showLabel=labelsVisible,modifier=toApps)
            StatusIcon(if(net.ethernet) AppIcons.Ethernet else if(net.wifi) AppIcons.Wifi else AppIcons.WifiOff,"网络",onNetworkClick,selected=networkOpen,showLabel=labelsVisible,modifier=toApps)
            StatusIcon(AppIcons.Gear,"桌面设置",onSettingsClick,onSettingsHold,settingsOpen,labelsVisible,modifier=toApps)
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, label: String, onClick: () -> Unit, onHold: (() -> Unit)? = null, selected: Boolean = false,showLabel:Boolean=false,spinning:Boolean=false,modifier:Modifier=Modifier) {
    var focused by remember { mutableStateOf(false) }
    val highlighted=focused || selected
    val foreground=if(highlighted) Color(0xFF171A20) else Color.White
    Surface(onClick=onClick,onLongClick=onHold ?: {},
        modifier=modifier.onFocusChanged { focused=it.isFocused },
        shape=ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(50)),
        colors=ClickableSurfaceDefaults.colors(containerColor=Color.Transparent,focusedContainerColor=Color.Transparent,
            contentColor=Color.White,focusedContentColor=Color(0xFF171A20)),
        scale=ClickableSurfaceDefaults.scale(focusedScale=1f)) {
        Box {
            if(highlighted) GlassSurface(Modifier.matchParentSize(),light=true) {}
            Row(Modifier.height(46.dp).padding(horizontal=if(showLabel) 14.dp else 12.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                if(spinning) {
                    val rotation=androidx.compose.animation.core.rememberInfiniteTransition(label="switching")
                    val degrees by rotation.animateFloat(initialValue=0f,targetValue=360f,
                        animationSpec=androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100,easing=androidx.compose.animation.core.LinearEasing)),label="rotation")
                    Icon(icon,label,Modifier.size(23.dp).graphicsLayer {rotationZ=degrees},tint=foreground)
                } else Icon(icon,label,Modifier.size(23.dp),tint=foreground)
                if(showLabel) Text(label,fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=1,color=foreground)
            }
        }
    }
}
