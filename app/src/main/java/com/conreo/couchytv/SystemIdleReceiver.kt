// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.content.*
import android.media.AudioManager
import android.os.*
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.ui.ScreenSaverRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** System-only idle signal, after the television checks video and keep-screen-on. */
class SystemIdleReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config=ConfigStore(context).flow.first()
                val own=config.saverEnabled && config.saverOwner==1
                if(intent.action==Intent.ACTION_BOOT_COMPLETED || intent.action==Intent.ACTION_MY_PACKAGE_REPLACED)
                    ScreenPower.configureSystemSaver(context,own,config.saverAfterMinutes)
                else if(own && config.saverAfterMinutes>0) {
                    ScreenPower.configureSystemSaver(context,true,config.saverAfterMinutes)
                    route(context)
                }
            } catch(e:Exception){android.util.Log.w("CouchySaver","idle signal failed",e)}
            finally {pending.finish()}
        }
    }
    companion object {
        private fun launchFailed(context:Context) {
            ScreenSaverRuntime.external.value=false
            CoroutineScope(Dispatchers.IO).launch {
                ScreenPower.configureSystemSaver(context,false,0)
                ScreenPower.lastError="屏保启动失败，已恢复电视屏保"
                android.util.Log.w("CouchySaver",ScreenPower.lastError)
            }
        }
        fun route(context:Context):Boolean {
            if(!context.getSystemService(PowerManager::class.java).isInteractive)return false
            if(context.getSystemService(AudioManager::class.java).isMusicActive)return false
            if(ScreenSaverRuntime.active.value || ScreenSaverRuntime.external.value || LauncherVisibility.saverResumed)return true
            if(LauncherVisibility.resumed) {
                Handler(Looper.getMainLooper()).post {ScreenSaverRuntime.preview()};return true
            }
            android.util.Log.i("CouchySaver","system idle: opening desktop saver")
            val previousStart=ScreenSaverRuntime.externalStarts
            ScreenSaverRuntime.external.value=true
            val result=ScreenPower.launchSystemIdleSaver(context)
            if(!result)launchFailed(context)
            else Handler(Looper.getMainLooper()).postDelayed({
                if(ScreenSaverRuntime.externalStarts==previousStart)launchFailed(context)
            },8000)
            return result
        }
    }
}
