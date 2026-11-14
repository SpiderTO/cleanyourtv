package com.gathin.screentime;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.SystemClock;

/**
 * Reads which package is in the foreground, from UsageStatsManager. Needs the PACKAGE_USAGE_STATS
 * app-op (granted over ADB). We track the most recent ACTIVITY_RESUMED event rather than polling
 * running processes, which Android no longer exposes to normal apps.
 */
final class AppMonitor {
    private final UsageStatsManager usm;
    private String lastForeground = "";
    private long lastQuery = 0;

    AppMonitor(Context c) {
        usm = (UsageStatsManager) c.getApplicationContext().getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /** The package currently in the foreground, or the last one seen if nothing changed. "" if unknown. */
    String foregroundPackage() {
        if (usm == null) return lastForeground;
        long now = System.currentTimeMillis();
        // The first query looks back to boot: after a cold start the service only runs once
        // BOOT_COMPLETED reaches it (about 40 s on the 43HS600AN), and an app opened before that, say
        // from the remote's YouTube button, must still be seen. Later queries pick up from the last one.
        long from = lastQuery > 0 ? lastQuery - 2_000
                : now - SystemClock.elapsedRealtime() - 60_000;
        lastQuery = now;
        UsageEvents events = usm.queryEvents(from, now);
        if (events == null) return lastForeground;
        UsageEvents.Event ev = new UsageEvents.Event();
        String latest = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            int type = ev.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latest = ev.getPackageName();
            }
        }
        if (latest != null) lastForeground = latest;
        return lastForeground;
    }

    /** True when UsageStats returns nothing at all, a sign the app-op was never granted. */
    boolean usableOrNull() {
        return usm != null;
    }
}
