package com.gathin.screentime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.Calendar;

/**
 * The always-on watcher. Once a second it asks which app is in the foreground and, while in kids
 * mode, counts time spent in the counted apps, warns near the limit, and throws up the lock screen
 * when the daily limit is reached, after the curfew hour, or when a blocked settings screen opens.
 *
 * Parent mode (set by entering the PIN) turns all of that off until the TV goes to standby, when the
 * screen-off receiver drops back to kids mode.
 */
public class TimeKeeperService extends Service {
    private static final String CHANNEL = "screentime";
    private static final int NOTIF_ID = 1;
    private static final long TICK_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private AppMonitor monitor;
    private String warnedDay = "";
    private long lastTick = 0;

    // TV and phone settings apps blocked in kids mode.
    private static final String[] SETTINGS_PKGS = {
            "com.android.tv.settings", "com.android.settings"
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                // Standby: drop out of parent mode so the next wake starts locked for the child.
                AppState.lock();
            } else if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_USER_PRESENT.equals(a)) {
                AppState.lock();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        monitor = new AppMonitor(this);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // specialUse FGS type only exists on API 34; below that the 2-arg form is fine.
            startForeground(NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, buildNotification());
        }
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;   // the OS restarts it if killed; it comes back in kids mode.
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_running))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                step();
            } finally {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private void step() {
        String fg = monitor.foregroundPackage();

        if (AppState.isParent()) {
            return;   // unlimited until standby
        }

        // Kids mode. Keep the child out of the TV settings.
        for (String s : SETTINGS_PKGS) {
            if (s.equals(fg)) {
                goHome();
                return;
            }
        }

        if (!prefs.isCounted(fg)) {
            return;   // digital TV, Netflix, our own screens, Home: not counted, not limited
        }

        // A counted app is open in kids mode.
        long now = System.currentTimeMillis();
        if (lastTick > 0) {
            long delta = Math.min(2000, now - lastTick);   // clamp if we were paused
            prefs.addUsedSeconds(Math.round(delta / 1000.0));
        }
        lastTick = now;

        int curfew = prefs.curfewHour();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (curfew > 0 && hour >= curfew) {   // 0 (or less) means the curfew is off
            block(LockActivity.REASON_CURFEW);
            return;
        }

        long used = prefs.usedSecondsToday();
        int limit = prefs.limitSeconds();
        if (used >= limit) {
            block(LockActivity.REASON_TIME_UP);
            return;
        }

        long remaining = limit - used;
        int warnSec = prefs.warnMinutes() * 60;
        if (warnSec > 0 && remaining <= warnSec && !Prefs.today().equals(warnedDay)) {
            warnedDay = Prefs.today();
            showWarning((int) Math.ceil(remaining / 60.0));
        }
    }

    /** A big, full-screen heads-up that the daily time is nearly gone. Our own activity, so the TV can
     *  screenshot it; it shows for a few seconds and returns to the app the child was watching. */
    private void showWarning(int minutesLeft) {
        Intent i = new Intent(this, WarningActivity.class);
        i.putExtra(WarningActivity.EXTRA_MINUTES, minutesLeft);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    /** Show the lock screen over the counted app. Reopening the app re-triggers this, so dismissing
     *  the lock without the PIN only bounces the child back to Home. */
    private void block(String reason) {
        lastTick = 0;
        Intent i = new Intent(this, LockActivity.class);
        i.putExtra(LockActivity.EXTRA_REASON, reason);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Start the service with the right foreground type across versions. */
    static void start(Context c) {
        Intent i = new Intent(c, TimeKeeperService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }
}
