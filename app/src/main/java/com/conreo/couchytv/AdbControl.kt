// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object AdbControl {
    fun setEnabled(context: Context, enabled: Boolean) {
        val intent = Intent().setComponent(ComponentName("com.ts.launcher", "com.homekey.standin.AdbKeeperService"))
            .setAction(if (enabled) "com.ts.launcher.ADB_KEEP_ON" else "com.ts.launcher.ADB_KEEP_OFF")
        runCatching {
            if (enabled) context.startForegroundService(intent) else context.startService(intent)
        }.onFailure { android.util.Log.w("CouchyCustom", "ADB keeper unavailable", it) }
    }
}
