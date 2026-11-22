package com.homekey.standin;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/** Optional owner-enabled keeper. Modified for Couchy HyperOS, 2026-11-23; MIT.
 * Does not hold a wake lock, accept new ADB keys, or start without explicit opt-in. */
public final class AdbKeeperService extends Service {
    private static final String TAG = "CouchyAdbKeeper";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() { maintain(); handler.postDelayed(this, 3000); }
    };
    private final ContentObserver observer = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { maintain(); }
    };

    public static void ensure(Context context) {
        if (context.getSharedPreferences("adb-keeper", MODE_PRIVATE).getBoolean("enabled", false)
                && context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            try { context.startForegroundService(new Intent(context, AdbKeeperService.class)); }
            catch (RuntimeException e) { Log.w(TAG, "Will retry when the launcher resumes", e); }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if ("com.ts.launcher.ADB_KEEP_OFF".equals(action)) {
            getSharedPreferences("adb-keeper", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("com.ts.launcher.ADB_KEEP_ON".equals(action)) {
            getSharedPreferences("adb-keeper", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        }
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED
                || !getSharedPreferences("adb-keeper", MODE_PRIVATE).getBoolean("enabled", false)) {
            Log.w(TAG, "ADB keeper not enabled or permission not granted");
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("adb-keeper", "ADB 连接维护", NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, "adb-keeper")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("ADB 自动保持开启")
                .setContentText("可在 Couchy 启动器设置中关闭；深度待机不会被阻止")
                .setOngoing(true).setShowWhen(false).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(5104, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(5104, notification);
        if (!registered) {
            getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, observer);
            registered = true;
        }
        handler.removeCallbacks(heartbeat);
        heartbeat.run();
        Log.i(TAG, "ADB maintenance running");
        return START_STICKY;
    }

    private void maintain() {
        try {
            if (Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 1) {
                Settings.Global.putInt(getContentResolver(), Settings.Global.ADB_ENABLED, 1);
                Log.i(TAG, "Restored ADB_ENABLED=1");
            }
        } catch (RuntimeException e) { Log.w(TAG, "Cannot restore ADB switch", e); }
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (registered) getContentResolver().unregisterContentObserver(observer);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
