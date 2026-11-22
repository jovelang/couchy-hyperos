// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Translucent until the saver is ready; the previous app stays underneath. */
class ScreenSaverActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenSaverRuntime.externalStarts++
        if(ScreenPower.lastError.startsWith("屏保启动失败"))ScreenPower.lastError=""
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.attributes=window.attributes.apply {windowAnimations=0}
        val previous=window.callback
        window.callback=object:android.view.Window.Callback by previous {
            override fun dispatchKeyEvent(event:KeyEvent):Boolean=
                if(ScreenSaverRuntime.onKey(event))true else previous.dispatchKeyEvent(event)
        }
        lifecycleScope.launch {
            val config=ConfigStore(this@ScreenSaverActivity).flow.first()
            if(!config.saverEnabled || config.saverOwner!=1) {finish();return@launch}
            ScreenSaverRuntime.external.value=true
            ScreenSaverRuntime.preview()
            setContent {LiteTvTheme(accent=ACCENTS[config.accent.coerceIn(ACCENTS.indices)]) {
                ScreenSaverHost(config,false,standalone=true,onExit={finish()})
            }}
        }
    }
    override fun onResume(){super.onResume();LauncherVisibility.saverResumed=true}
    override fun onPause(){LauncherVisibility.saverResumed=false;super.onPause()}
    override fun onStop(){ScreenSaverRuntime.wake();super.onStop();finish()}
    override fun onDestroy(){LauncherVisibility.saverResumed=false;ScreenSaverRuntime.external.value=false;super.onDestroy()}
}
