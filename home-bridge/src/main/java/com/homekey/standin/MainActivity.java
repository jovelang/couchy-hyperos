package com.homekey.standin;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

/** Modified 2026-11-23 from fandejin/homekey-standin (MIT; see LICENSE). */
public class MainActivity extends Activity {
    private static final String TAG = "CouchyHomeBridge";
    private static final long RETRY_WINDOW_MS = 30000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long deadline;
    private int attempts;
    private final Runnable retry = new Runnable() {
        @Override public void run() { forwardWhenReady(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        beginForwarding();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        beginForwarding();
    }

    private void beginForwarding() {
        handler.removeCallbacks(retry);
        attempts = 0;
        deadline = SystemClock.uptimeMillis() + RETRY_WINDOW_MS;
        forwardWhenReady();
    }

    private void forwardWhenReady() {
        if (isFinishing() || isDestroyed()) return;
        attempts++;
        UserManager users = (UserManager) getSystemService(USER_SERVICE);
        boolean unlocked = users == null || users.isUserUnlocked();
        if (unlocked) {
            AdbKeeperService.ensure(this);
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                ComponentName component = new ComponentName(BuildConfig.TARGET_PACKAGE, "com.conreo.couchytv.MainActivity");
                try { getPackageManager().getActivityInfo(component, 0); }
                catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
                    component = new ComponentName("com.conreo.couchytv", "com.conreo.couchytv.MainActivity");
                }
                intent.setComponent(component);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
                Log.i(TAG, "Forwarded to Couchy; attempts=" + attempts);
                finish();
                overridePendingTransition(0, 0);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                if (attempts == 1 || attempts % 10 == 0) {
                    Log.w(TAG, "Couchy is not ready; will retry in the foreground", e);
                }
            }
        } else if (attempts == 1) {
            Log.i(TAG, "Waiting for user unlock before starting Couchy");
        }
        if (SystemClock.uptimeMillis() < deadline) {
            handler.postDelayed(retry, 500);
            return;
        }
        Log.e(TAG, "Couchy unavailable after 30 seconds; opening Settings");
        try {
            Intent settings = new Intent("android.settings.SETTINGS");
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settings);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "Settings fallback unavailable", e);
        }
        finish();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(retry);
        super.onDestroy();
    }
}
