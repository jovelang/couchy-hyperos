// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.conreo.couchytv.ui.LauncherApp
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

object LauncherVisibility { @Volatile var resumed=false;@Volatile var saverResumed=false }

class MainActivity : ComponentActivity() {
    override fun onResume() {super.onResume();LauncherVisibility.resumed=true}
    override fun onPause() {LauncherVisibility.resumed=false;remoteKeys.cancel();super.onPause()}


    /** Bumped whenever a package is installed/removed so the app grid rescans. */
    var rescanTick by mutableIntStateOf(0)
        private set
    var homeResetTick by mutableIntStateOf(0)
        private set

    var remoteActionTick by mutableIntStateOf(0)
        private set
    var remoteAction by mutableIntStateOf(0)
        private set
    private var keyConfig = com.conreo.couchytv.data.LauncherConfig()
    private val remoteKeys = RemoteKeyRouter({keyConfig}) { value -> remoteAction=value;remoteActionTick++ }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean =
        if(remoteKeys.handle(event)) true else super.onKeyDown(keyCode,event)
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        if(remoteKeys.handle(event)) true else super.onKeyUp(keyCode,event)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            // Removals always rescan (the app may be on the grid). For installs and
            // changes, skip the full scan unless the package is actually launchable.
            val launchable = pkg != null && (
                packageManager.getLeanbackLaunchIntentForPackage(pkg) != null ||
                    packageManager.getLaunchIntentForPackage(pkg) != null
                )
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED || launchable) {
                rescanTick++
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Read the chosen UI language synchronously from SharedPreferences — cheap,
        // and off the DataStore (a runBlocking DataStore read here blocked the main
        // thread and made language changes hang until a restart). LauncherApp keeps
        // this mirror in sync with the DataStore config and triggers recreate().
        val lang = newBase.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            .getString(LOCALE_KEY, "").orEmpty()
        super.attachBaseContext(if (lang.isEmpty()) newBase else applyLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The Home bridge must not dim the cached wallpaper while its surface reconnects.
        if(android.os.Build.VERSION.SDK_INT>=34) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN,0,0)
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE,0,0)
        }
        window.attributes=window.attributes.apply {windowAnimations=0}
        // Handle the two remote keys before Compose consumes an unfocused event.
        val previousCallback=window.callback
        window.callback=object:android.view.Window.Callback by previousCallback {
            override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean {
                if(com.conreo.couchytv.ui.ScreenSaverRuntime.onKey(event))return true
                return if(remoteKeys.handle(event)) true else previousCallback.dispatchKeyEvent(event)
            }
        }
        lifecycleScope.launch {
            com.conreo.couchytv.data.ConfigStore(this@MainActivity).flow.collect { keyConfig=it }
        }
        lifecycleScope.launch {
            com.conreo.couchytv.data.ConfigStore(this@MainActivity).flow
                .map {Triple(it.saverEnabled,it.saverOwner,it.saverAfterMinutes)}.distinctUntilChanged()
                .collect {(enabled,owner,minutes)->ScreenPower.configureSystemSaver(this@MainActivity,enabled && owner==1,minutes)}
        }
        if(intent?.action==com.conreo.couchytv.AppIdentity.REMOTE_ACTION) {
            remoteAction=intent.getIntExtra("remote_action",0);remoteActionTick++
        }

        // A home screen never exits on Back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op */ }
        })

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            LauncherApp(rescanTick, homeResetTick, remoteActionTick, remoteAction)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {com.conreo.couchytv.ui.ScreenSaverRuntime.wake();homeResetTick++}
        if (intent.action == com.conreo.couchytv.AppIdentity.REMOTE_ACTION) {
            remoteAction=intent.getIntExtra("remote_action",0);remoteActionTick++
        }
    }

    override fun onDestroy() {
        remoteKeys.cancel()
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    companion object {
        private const val LOCALE_PREFS = "locale"
        private const val LOCALE_KEY = "lang"

        /** Mirror the chosen language so attachBaseContext can read it synchronously. */
        fun persistLocale(context: Context, lang: String) {
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .edit().putString(LOCALE_KEY, lang).apply()
        }

        fun currentLocalePref(context: Context): String =
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(LOCALE_KEY, "").orEmpty()

        fun applyLocale(context: Context, lang: String): Context {
            val locale = if (lang.contains('-')) {
                val parts = lang.split('-')
                Locale(parts[0], parts.getOrElse(1) { "" })
            } else Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}
