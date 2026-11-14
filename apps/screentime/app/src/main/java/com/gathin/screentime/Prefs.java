package com.gathin.screentime;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * All stored state for the screen-time app: the parent PIN, the daily limit, the curfew window, which
 * apps are counted, the strict-mode allow list, and how many seconds each counted app has run today.
 * Tiny on purpose — one SharedPreferences file, read on demand. Nothing here touches the UI thread for long.
 */
final class Prefs {
    private static final String FILE = "screentime";
    private static final String KEY_PIN = "pin";
    private static final String KEY_LIMIT_MIN = "limit_minutes";
    private static final String KEY_WARN_MIN = "warn_minutes";
    private static final String KEY_CURFEW_HOUR = "curfew_hour";            // curfew start hour
    private static final String KEY_CURFEW_END_HOUR = "curfew_end_hour";    // curfew end hour (next morning)
    private static final String KEY_STRICT = "strict_when_blocked";
    private static final String KEY_ALLOWED = "allowed_when_blocked";
    private static final String KEY_COUNTED = "counted_apps";
    private static final String KEY_USAGE_PREFIX = "usage_";   // usage_<yyy-MM-dd> -> seconds today

    static final String YOUTUBE = "com.google.android.youtube.tv";
    static final String YOUTUBE_KIDS = "com.google.android.youtube.tvkids";

    /** App families counted as one: tick any member and every package with the prefix is counted, so
     *  YouTube Music (com.google.android.youtube.tvmusic) and friends do not slip past the limit. */
    private static final String[] FAMILY_PREFIXES = {"com.google.android.youtube"};

    /** Apps still allowed in strict mode once the time is up: MediaTek live TV and its inputs. The Home
     *  app and this app are always allowed on top of these. */
    static final String[] DEFAULT_ALLOWED = {"com.mediatek.wwtv.tvcenter", "com.mediatek.tvinput"};

    static final int DEFAULT_LIMIT_MIN = 120;   // 2 hours per day (YouTube + YouTube Kids)
    static final int DEFAULT_WARN_MIN = 5;      // warn 5 minutes before the limit
    static final int DEFAULT_CURFEW_HOUR = 21;  // curfew from 21:00 ...
    static final int DEFAULT_CURFEW_END_HOUR = 7;   // ... to 07:00 (start == end: off)

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- PIN -------------------------------------------------------------------------------------
    boolean hasPin() {
        String p = sp.getString(KEY_PIN, "");
        return p != null && p.length() == 4;
    }

    boolean checkPin(String entered) {
        return hasPin() && sp.getString(KEY_PIN, "").equals(entered);
    }

    void setPin(String pin) {
        sp.edit().putString(KEY_PIN, pin).apply();
    }

    // ---- limits ----------------------------------------------------------------------------------
    int limitMinutes() {
        return sp.getInt(KEY_LIMIT_MIN, DEFAULT_LIMIT_MIN);
    }

    void setLimitMinutes(int m) {
        sp.edit().putInt(KEY_LIMIT_MIN, Math.max(1, m)).apply();
    }

    int warnMinutes() {
        return sp.getInt(KEY_WARN_MIN, DEFAULT_WARN_MIN);
    }

    void setWarnMinutes(int m) {
        sp.edit().putInt(KEY_WARN_MIN, Math.max(0, m)).apply();
    }

    int curfewHour() {
        return sp.getInt(KEY_CURFEW_HOUR, DEFAULT_CURFEW_HOUR);
    }

    void setCurfewHour(int h) {
        sp.edit().putInt(KEY_CURFEW_HOUR, h).apply();
    }

    int curfewEndHour() {
        // Before the end hour existed, a start of 0 meant "off": keep it off for those installs.
        if (!sp.contains(KEY_CURFEW_END_HOUR) && sp.contains(KEY_CURFEW_HOUR) && curfewHour() == 0) return 0;
        return sp.getInt(KEY_CURFEW_END_HOUR, DEFAULT_CURFEW_END_HOUR);
    }

    void setCurfewEndHour(int h) {
        sp.edit().putInt(KEY_CURFEW_END_HOUR, h).apply();
    }

    /** True when `hour` falls in [start, end), wrapping past midnight when start > end (21 -> 7). */
    boolean inCurfew(int hour) {
        int start = curfewHour(), end = curfewEndHour();
        if (start == end) return false;
        if (start < end) return hour >= start && hour < end;
        return hour >= start || hour < end;
    }

    // ---- strict mode -----------------------------------------------------------------------------
    /** Once the time is up or in the curfew, block every app except the allowed ones, not just the
     *  counted ones. */
    boolean strictWhenBlocked() {
        return sp.getBoolean(KEY_STRICT, false);
    }

    void setStrictWhenBlocked(boolean on) {
        sp.edit().putBoolean(KEY_STRICT, on).apply();
    }

    Set<String> allowedWhenBlocked() {
        Set<String> dflt = new HashSet<>();
        for (String p : DEFAULT_ALLOWED) dflt.add(p);
        return new HashSet<>(sp.getStringSet(KEY_ALLOWED, dflt));
    }

    void setAllowedWhenBlocked(Set<String> apps) {
        sp.edit().putStringSet(KEY_ALLOWED, new HashSet<>(apps)).apply();
    }

    // ---- counted apps ----------------------------------------------------------------------------
    Set<String> countedApps() {
        Set<String> dflt = new HashSet<>();
        dflt.add(YOUTUBE);
        dflt.add(YOUTUBE_KIDS);
        return new HashSet<>(sp.getStringSet(KEY_COUNTED, dflt));
    }

    boolean isCounted(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        Set<String> counted = countedApps();
        if (counted.contains(pkg)) return true;
        for (String prefix : FAMILY_PREFIXES) {
            if (!pkg.startsWith(prefix)) continue;
            for (String c : counted) {
                if (c.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    void setCountedApps(Set<String> apps) {
        sp.edit().putStringSet(KEY_COUNTED, new HashSet<>(apps)).apply();
    }

    // ---- daily usage -----------------------------------------------------------------------------
    /** Local calendar day as yyyy-MM-dd, so the total rolls over at local midnight. */
    static String today() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    long usedSecondsToday() {
        return sp.getLong(KEY_USAGE_PREFIX + today(), 0L);
    }

    void addUsedSeconds(long seconds) {
        String key = KEY_USAGE_PREFIX + today();
        long now = sp.getLong(key, 0L) + seconds;
        // Keep only today's counter; drop any stale day keys so the file never grows.
        SharedPreferences.Editor e = sp.edit();
        for (String k : new HashSet<>(sp.getAll().keySet())) {
            if (k.startsWith(KEY_USAGE_PREFIX) && !k.equals(key)) e.remove(k);
        }
        e.putLong(key, now).apply();
    }

    /** Parent "give more time now": clear today's counter so the child gets the full limit again. */
    void resetToday() {
        sp.edit().putLong(KEY_USAGE_PREFIX + today(), 0L).apply();
    }

    int limitSeconds() {
        return limitMinutes() * 60;
    }
}
