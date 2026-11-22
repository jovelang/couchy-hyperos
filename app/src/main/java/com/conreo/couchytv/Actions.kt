// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object Actions {

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun launchApp(context: Context, pkg: String) {
        if(pkg == com.conreo.couchytv.AppIdentity.SETTINGS_ENTRY_ID) {
            context.startActivity(Intent(context,MainActivity::class.java).setAction(com.conreo.couchytv.AppIdentity.REMOTE_ACTION)
                .putExtra("remote_action",3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val at=android.os.SystemClock.uptimeMillis()
        val cached=com.conreo.couchytv.data.AppRepository.launchIntent(pkg)
        val pm = context.packageManager
        val intent = cached ?: pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            android.util.Log.i("CouchyLaunch","resolve pkg=$pkg cached=${cached!=null} ms=${android.os.SystemClock.uptimeMillis()-at}")
            runCatching { startExternal(context,intent) }
                .onFailure { toast(context, context.getString(R.string.toast_cannot_open)) }
        } else toast(context, context.getString(R.string.toast_no_launchable))
    }

    private fun startExternal(context:Context,intent:Intent) {
        val at=android.os.SystemClock.uptimeMillis()
        WallpaperLaunchGate.prepare()
        try {context.startActivity(intent)} catch(e:Exception) {WallpaperLaunchGate.failed();throw e}
        android.util.Log.i("CouchyLaunch","dispatch component=${intent.component} ms=${android.os.SystemClock.uptimeMillis()-at}")
    }

    fun openAppInfo(context: Context, pkg: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                )
            )
        }
    }

    fun uninstall(context: Context, pkg: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        }
    }

    /** Best-effort: clears the app from cached memory. True force-stop is in App info. */
    fun close(context: Context, pkg: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        runCatching { am.killBackgroundProcesses(pkg) }
    }

    fun openSystemSettings(context: Context) {
        val native = Intent().setComponent(android.content.ComponentName("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startExternal(context,native) }
            .onFailure { runCatching { startExternal(context,Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    fun openNetworkSettings(context: Context) {
        val native=Intent("com.xiaomi.mitv.settings.WIFI_SETTINGS")
            .setPackage("com.xiaomi.mitv.settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startExternal(context,native) }.onFailure {
            runCatching {startExternal(context,Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                .onFailure {openSystemSettings(context)}
        }
    }

    fun openVpnSettings(context: Context) {
        runCatching { startExternal(context,Intent("android.settings.VPN_SETTINGS")) }
            .onFailure { openSystemSettings(context) }
    }

    /** AerialViews screensaver (github.com/theothernt/AerialViews) */
    const val AERIAL_PKG = "com.neilturner.aerialviews"

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun openAppStore(context: Context, pkg: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        }.onFailure {
            toast(context, context.getString(R.string.toast_no_store))
        }
    }

}
