// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.ui

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Original rounded monoline symbols, optically matched to the Apple TV interface. */
private fun symbol(name:String,path:String)=ImageVector.Builder(name,24.dp,24.dp,24f,24f)
    .addPath(pathData=addPathNodes(path),fill=null,stroke=SolidColor(Color.White),
        strokeLineWidth=1.65f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()

object AppIcons {
    val Cycle by lazy {symbol("Cycle","M19 8A8 8 0 1 0 20 14 M19 3v5h-5")}
    val Desktop by lazy {symbol("Desktop","M4 4h16a1 1 0 0 1 1 1v12H3V5a1 1 0 0 1 1-1Z M8 21h8 M12 17v4 M6 7h4v4H6Z M13 7h5 M13 10h5 M6 14h12")}
    val Television by lazy {symbol("Television","M4 4.5h16a1.5 1.5 0 0 1 1.5 1.5v11H2.5V6A1.5 1.5 0 0 1 4 4.5Z M8 21h8 M12 17v4")}
    val Input by lazy {symbol("Input","M9 5h11v14H9 M3 12h12 M11 8l4 4-4 4")}
    val Play by lazy {symbol("Play","M8 5.5v13l11-6.5Z")}
    val Bolt by lazy {symbol("Bolt","M13.5 2 5 13h6l-.5 9L19 10h-6Z")}
    val Move by lazy {symbol("Move","M12 3v18 M3 12h18 M8 7l4-4 4 4 M8 17l4 4 4-4 M7 8l-4 4 4 4 M17 8l4 4-4 4")}
    val Show by lazy {symbol("Show","M2 12C6 4 18 4 22 12 18 20 6 20 2 12Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0")}
    val Hide by lazy {symbol("Hide","M2 12c1.1-2.2 2.7-3.9 4.6-4.9 M10 5.7C15 4.7 19.6 7 22 12a14 14 0 0 1-3.2 4.2 M14.8 18.1C9.6 19.3 4.7 17.4 2 12 M3 3l18 18")}
    val Info by lazy {symbol("Info","M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 11v6 M12 7.5v.1")}
    val Stop by lazy {symbol("Stop","M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M8.5 8.5h7v7h-7Z")}
    val Delete by lazy {symbol("Delete","M4 6h16 M9 3h6v3 M6 6l1 15h10l1-15 M10 10v7 M14 10v7")}
    val Add by lazy {symbol("Add","M12 4v16 M4 12h16")}
    val Up by lazy {symbol("Up","M12 21V3 M5 10l7-7 7 7")}
    val Down by lazy {symbol("Down","M12 3v18 M5 14l7 7 7-7")}
    val Vpn by lazy {symbol("Vpn","M10 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M10 12h12 M17 12v4 M21 12v3")}
    val Wifi by lazy {symbol("Wifi","M3 8.5a14 14 0 0 1 18 0 M6.5 12.5a9 9 0 0 1 11 0 M10 16.5a3.5 3.5 0 0 1 4 0 M12 20v.1")}
    val WifiOff by lazy {symbol("WifiOff","M3 8.5a14 14 0 0 1 2-1.4 M9 5.5a14 14 0 0 1 12 3 M6.5 12.5a9 9 0 0 1 3-1.8 M15 11.3a9 9 0 0 1 2.5 1.2 M10 16.5a3.5 3.5 0 0 1 4 0 M12 20v.1 M3 3l18 18")}
    val Ethernet by lazy {symbol("Ethernet","M8 3h8v5h4v9H4V8h4Z M8 12v2 M12 12v2 M16 12v2 M12 17v4")}
    val Check by lazy {symbol("Check","M4 12.5 9.5 18 20 6")}
    val Pencil by lazy {symbol("Pencil","m4 16 12-12 4 4L8 20H4Z M13 7l4 4")}
    val Image by lazy {symbol("Image","M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z M3 16l6-6 4 4 3-3 5 5 M16 7v.1")}
    val Apps by lazy {symbol("Apps","M4 4h6v6H4Z M14 4h6v6h-6Z M4 14h6v6H4Z M14 14h6v6h-6Z")}
    val Save by lazy {symbol("Save","M5 3h12l4 4v14H3V3Z M7 3v6h10V3 M7 21v-7h10v7")}
    val Folder by lazy {symbol("Folder","M3 6h7l2 2h9v12H3Z M3 6V4h7l2 2h9v2")}
    val Back by lazy {symbol("Back","M20 12H4 M10 6l-6 6 6 6")}
    val Gear by lazy {symbol("Gear","M9 3h6l.5 2.5 2 1.2 2.4-.8 2.5 4.2-2 1.9v2.3l2 1.8-2.5 4.3-2.4-.8-2 1.2L15 23H9l-.5-2.5-2-1.2-2.4.8-2.5-4.3 2-1.8v-2.3l-2-1.9 2.5-4.2 2.4.8 2-1.2Z M15.5 13a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0")}
    val Display by lazy {symbol("Display","M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M10 15v6")}
    val Palette by lazy {symbol("Palette","M12 3a9 9 0 1 0 0 18h1.5a2 2 0 0 0 1.4-3.4c-.9-.9-.3-2.6 1.1-2.6h2a4 4 0 0 0 4-4c0-4.4-4.5-8-10-8 M7 10v.1 M10 6.5v.1 M15 6.5v.1 M18 10v.1")}
    val Language by lazy {symbol("Language","M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M3 12h18 M12 3c-5 5-5 13 0 18 M12 3c5 5 5 13 0 18 M5 7h14 M5 17h14")}
    val Clock by lazy {symbol("Clock","M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 6v6l4 3")}
    val Calendar by lazy {symbol("Calendar","M4 5h16v16H4Z M4 10h16 M8 3v4 M16 3v4 M8 14h.1 M12 14h.1 M16 14h.1 M8 18h.1 M12 18h.1")}
    val Person by lazy {symbol("Person","M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2")}
    val Remote by lazy {symbol("Remote","M9 2h6a3 3 0 0 1 3 3v14a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3Z M14.5 8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0 M10 14h4 M10 18h.1 M14 18h.1")}
    val Weather by lazy {symbol("Weather","M8 18h9a4 4 0 1 0-.6-7.9 5 5 0 0 0-9.7 1.1A3.5 3.5 0 0 0 8 18Z M5 3v2 M1.5 6 3 7 M9 4l1-1 M2 11H.5")}
    val Moon by lazy {symbol("Moon","M19.5 15.5a8.5 8.5 0 0 1-11-11 8.5 8.5 0 1 0 11 11")}
    val Location by lazy {symbol("Location","M19 9c0 5-7 12-7 12S5 14 5 9a7 7 0 1 1 14 0 M14 9a2 2 0 1 1-4 0 2 2 0 0 1 4 0")}
    val Chevron by lazy {symbol("Chevron","M9 5l7 7-7 7")}
}
