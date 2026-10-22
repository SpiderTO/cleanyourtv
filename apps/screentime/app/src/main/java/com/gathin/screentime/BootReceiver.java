package com.gathin.screentime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts the watcher as soon as the TV finishes booting, so kids mode is in force from the first
 * moment the home screen appears. AppState defaults to kids mode, so no extra work is needed here.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)) {
            AppState.lock();
            TimeKeeperService.start(context);
        }
    }
}
