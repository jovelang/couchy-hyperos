// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.tv.TvContract

data class InputSource(val info: TvInputInfo, val name: String) {
    val id get() = info.id
    fun label(config: LauncherConfig): String = config.inputAliases[name]?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it-$name" } ?: name
}

object SourceController {
    val targets=listOf("HDMI1","HDMI2","HDMI3","HDMI4","TV","AV","DTMB")
    fun perform(context:Context,action:Int,config:LauncherConfig):InputSource? {
        if(action==4) return cycle(context,config)
        val name=targets.getOrNull(action-5) ?: return null
        val input=list(context).firstOrNull {it.name==name} ?: return null
        open(context,input)
        return input
    }

    fun list(context: Context): List<InputSource> = runCatching {
        context.getSystemService(TvInputManager::class.java).tvInputList
            .filter { !runCatching { it.isHidden(context) }.getOrDefault(false) }.distinctBy { it.id }
            .map { input ->
                val raw = input.loadLabel(context).toString()
                val hw = input.id.substringAfterLast('/').removePrefix("HW").toIntOrNull()
                val name = when {
                    input.type == TvInputInfo.TYPE_HDMI -> "HDMI" + (if (hw in 5..8) hw!! - 4 else raw.filter { it.isDigit() }.toIntOrNull() ?: 1)
                    input.type == TvInputInfo.TYPE_COMPOSITE -> "AV"
                    raw.contains("ATV", true) -> "TV"
                    raw.contains("DTV", true) || raw.contains("DTMB", true) -> "DTMB"
                    else -> raw
                }
                InputSource(input, name)
            }.sortedWith(compareBy({ if (it.name.startsWith("HDMI")) 0 else 1 }, { when(it.name) { "TV" -> "1"; "AV" -> "2"; "DTMB" -> "3"; else -> it.name } }))
    }.getOrDefault(emptyList())

    fun open(context: Context, source: InputSource) {
        val action = "com.xiaomi.mitv.tvplayer.EXTSRC_PLAY"
        if (context.packageManager.resolveActivity(Intent(action).setPackage("com.xiaomi.mitv.tvplayer"), 0) != null) {
            val intent = when {
                source.name.startsWith("HDMI") -> Intent(action).putExtra("input", source.name.removePrefix("HDMI").toInt() + 22)
                source.name == "AV" -> Intent(action).putExtra("input", 2)
                source.name == "TV" -> Intent("com.xiaomi.mitv.tvplayer.ATV_PLAY")
                source.name == "DTMB" -> Intent("com.xiaomi.mitv.tvplayer.DTMB_PLAY")
                else -> null
            }
            if (intent != null) {
                context.startActivity(intent.setPackage("com.xiaomi.mitv.tvplayer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(source.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun cycle(context: Context, config: LauncherConfig): InputSource? {
        val sources = list(context)
        if (sources.isEmpty()) return null
        val next = sources[(sources.indexOfFirst { it.id == config.lastInput } + 1) % sources.size]
        open(context, next)
        return next
    }
}
