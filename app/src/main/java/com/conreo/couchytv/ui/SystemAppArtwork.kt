// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.conreo.couchytv.data.AppEntry

private fun weather(app: AppEntry) = app.pkg.contains("weather", true) || app.label.contains("天气")
fun isSystemArtwork(app: AppEntry) = weather(app) || app.pkg in setOf("com.xiaomi.mitv.settings", "com.android.tv.settings", "com.android.settings", com.conreo.couchytv.AppIdentity.SETTINGS_ENTRY_ID)

@Composable
fun SystemAppArtwork(app: AppEntry) {
    val weather = weather(app)
    val desktop = app.pkg == com.conreo.couchytv.AppIdentity.SETTINGS_ENTRY_ID
    val colors = if (weather) listOf(Color(0xFF0B95D5), Color(0xFF67D8EA))
        else if(desktop) listOf(Color(0xFF4A46B8), Color(0xFF8678DF)) else listOf(Color(0xFF116D76), Color(0xFF3CA997))
    Row(Modifier.fillMaxSize().background(Brush.linearGradient(colors)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (weather) {
            Icon(AppIcons.Weather,null,Modifier.size(36.dp),tint=Color.White)
        } else Icon(if(desktop) AppIcons.Gear else AppIcons.Television, null, Modifier.size(36.dp), tint = Color.White)
        Text(if (weather) "天气" else if(desktop) "桌面设置" else if(app.pkg=="com.android.tv.settings") "Android 设置" else "电视设置", modifier = Modifier.weight(1f), color = Color.White,
            fontSize = if (weather) 22.sp else 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
