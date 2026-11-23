// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Couchy HyperOS contributors.
package com.homekey.standin;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

/** Opt-in persistent HOME policy stored and applied entirely on the television. */
public final class HomePolicy {
    private static final String TAG = "CouchyHomePolicy";
    private static final Object LOCK = new Object();

    private static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("persistent-home", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean("enabled", false);
    }

    public static Bundle changeEnabled(Context context, boolean enabled) {
        synchronized (LOCK) {
            if (!prefs(context).edit().putBoolean("enabled", enabled).commit()) {
                throw new IllegalStateException("Cannot save HOME policy preference");
            }
            return apply(context, enabled ? "install" : "remove",
                    enabled ? "explicit-device-setup" : "explicit-device-restore");
        }
    }

    public static Bundle status(Context context) {
        SharedPreferences prefs = prefs(context);
        Bundle result = new Bundle();
        result.putBoolean("enabled", enabled(context));
        result.putBoolean("lastCommandAccepted", prefs.getBoolean("accepted", false));
        result.putLong("lastRequestedAt", prefs.getLong("requestedAt", 0));
        result.putString("lastReason", prefs.getString("reason", ""));
        result.putString("lastError", prefs.getString("error", ""));
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo info = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null && info.activityInfo != null) {
            result.putString("resolvedHome", new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString());
        }
        return result;
    }

    public static Bundle apply(Context context, String action, String reason) {
        if (!("install".equals(action) || "remove".equals(action))) throw new IllegalArgumentException("Invalid HOME operation");
        synchronized (LOCK) {
            // A queued boot callback must not reinstall after an explicit restore.
            if ("install".equals(action) && !enabled(context)) return status(context);
            boolean accepted = false;
            String error = "";
            try {
                String apk = context.getApplicationInfo().sourceDir;
                if (!apk.matches("[a-zA-Z0-9/_~.+=-]+")) throw new IllegalStateException("Unexpected APK path");
                Class<?> backend = Class.forName("mitv.impl.util.TvStateGc");
                backend.getMethod("getScreenSaverTimeout").invoke(null);
                java.lang.reflect.Field field = backend.getDeclaredField("mTvService");
                field.setAccessible(true);
                Object service = field.get(null);
                if (service == null) throw new IllegalStateException("TV system service is not ready");
                String command = "/system/bin/env CLASSPATH=" + apk
                        + " /system/bin/app_process /system/bin com.homekey.standin.HomePolicyCommand " + action;
                accepted = (Boolean) Class.forName("mitv.internal.ITvService")
                        .getMethod("runSystemCommand", String.class).invoke(service, command);
                if (!accepted) throw new IllegalStateException("TV system service declined HOME operation");
            } catch (Throwable failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
                Log.w(TAG, "HOME policy request unavailable", cause);
            }
            prefs(context).edit().putBoolean("accepted", accepted).putString("error", error)
                    .putLong("requestedAt", System.currentTimeMillis()).putString("reason", reason).commit();
            Log.i(TAG, "Requested " + action + "; accepted=" + accepted + "; reason=" + reason);
            return status(context);
        }
    }
}
