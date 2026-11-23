// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Couchy HyperOS contributors.
package com.homekey.standin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.util.Log;

/** Boot/update checks only. The system stores the actual policy; no polling daemon. */
public final class HomeBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (!(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))) return;
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (HomePolicy.enabled(context)) HomePolicy.apply(context, "install", action);
                    UserManager users = context.getSystemService(UserManager.class);
                    if (users == null || users.isUserUnlocked()) AdbKeeperService.ensure(context);
                } catch (RuntimeException error) {
                    Log.w("CouchyHomePolicy", "Boot integration deferred", error);
                } finally { pending.finish(); }
            }
        }, "couchy-home-boot").start();
    }
}
