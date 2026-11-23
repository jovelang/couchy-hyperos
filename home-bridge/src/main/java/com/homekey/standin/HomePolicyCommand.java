// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Couchy HyperOS contributors.
package com.homekey.standin;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;

/** Fixed HOME-only entry run by the TV system service. No arbitrary command API. */
public final class HomePolicyCommand {
    private static final String TAG = "CouchyHomePolicy";
    private static final ComponentName HOME = new ComponentName(
            "com.ts.launcher", "com.homekey.standin.MainActivity");

    public static void main(String[] args) {
        int exit = 1;
        try {
            if (Process.myUid() != Process.SYSTEM_UID) throw new SecurityException("System service only");
            if (args.length != 1 || !("install".equals(args[0]) || "remove".equals(args[0]))) {
                throw new IllegalArgumentException("Expected fixed HOME operation");
            }
            Looper.prepareMainLooper();
            Class<?> api = Class.forName("android.content.pm.IPackageManager");
            Object pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null);
            if ("remove".equals(args[0])) {
                api.getMethod("clearPackagePersistentPreferredActivities", String.class, int.class)
                        .invoke(pm, HOME.getPackageName(), 0);
                Log.i(TAG, "Removed our persistent HOME preference; uid=" + Process.myUid());
            } else {
                ActivityInfo info = (ActivityInfo) api.getMethod("getActivityInfo", ComponentName.class, long.class, int.class)
                        .invoke(pm, HOME, 0L, 0);
                if (info == null || !info.enabled || !info.applicationInfo.enabled) {
                    throw new IllegalStateException("HOME bridge is unavailable");
                }
                IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
                filter.addCategory(Intent.CATEGORY_HOME);
                filter.addCategory(Intent.CATEGORY_DEFAULT);
                // Replace only our own records, keeping repeated boot checks bounded.
                api.getMethod("clearPackagePersistentPreferredActivities", String.class, int.class)
                        .invoke(pm, HOME.getPackageName(), 0);
                api.getMethod("addPersistentPreferredActivity", IntentFilter.class, ComponentName.class, int.class)
                        .invoke(pm, filter, HOME, 0);
                Log.i(TAG, "Installed persistent HOME=" + HOME.flattenToString() + "; uid=" + Process.myUid());
            }
            exit = 0;
        } catch (Throwable error) {
            if (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
            Log.e(TAG, "Persistent HOME operation failed", error);
        }
        System.exit(exit);
    }
}
