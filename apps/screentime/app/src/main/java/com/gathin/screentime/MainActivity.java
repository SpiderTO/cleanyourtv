package com.gathin.screentime;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * The app's own screen, shown as a tile in the launcher. Tells the parent which mode the TV is in
 * and how much of today's time is used, and offers: unlock parent mode (PIN), open settings (PIN),
 * lock back to kids mode, and give more time today. On first run it asks the parent to set a PIN.
 */
public class MainActivity extends Activity {
    private Prefs prefs;
    private LinearLayout column;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        TimeKeeperService.start(this);

        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFF0B0B0F);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(40);
        column.setPadding(pad, pad, pad, pad);
        root.addView(column);
        setContentView(root);

        if (!prefs.hasPin()) {
            PinPadView.show(this, getString(R.string.first_run_title),
                    PinPadView.Mode.SET, prefs, success -> render());
        } else {
            render();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.hasPin() && !PinPadView.isShowing()) render();
    }

    private void render() {
        column.removeAllViews();

        addTitle(getString(R.string.app_name));
        addText(AppState.isParent() ? getString(R.string.status_parent)
                : getString(R.string.status_kids), 18, 0xFF4FA3FF);

        long used = prefs.usedSecondsToday();
        addText(getString(R.string.used_today, clock(used), clock(prefs.limitSeconds())), 16, 0xFFEDEDED);

        if (!hasUsageAccess()) {
            addText(getString(R.string.need_usage_access), 15, 0xFFFFB020);
        }

        addGap();

        if (AppState.isKids()) {
            addButton(getString(R.string.unlock_parent), () ->
                    PinPadView.show(this, getString(R.string.enter_pin), PinPadView.Mode.VERIFY, prefs, ok -> {
                        if (ok) AppState.unlock();
                        render();
                    }));
        } else {
            addButton(getString(R.string.lock_now), () -> {
                AppState.lock();
                render();
            });
            addButton(getString(R.string.give_more_time), () -> {
                prefs.resetToday();
                render();
            });
        }

        // SettingsActivity asks for the PIN itself (it is also reachable directly), so no second prompt here.
        addButton(getString(R.string.open_settings), () ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    // ---- small view helpers ----------------------------------------------------------------------
    private void addTitle(String s) {
        addText(s, 30, Color.WHITE);
    }

    private void addText(String s, int sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sizeSp);
        t.setPadding(0, dp(6), 0, dp(6));
        column.addView(t);
    }

    private void addGap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        column.addView(v);
    }

    private void addButton(String label, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setBackgroundResource(R.drawable.key_bg);
        int px = dp(18);
        b.setPadding(px, px, px, px);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        column.addView(b);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    static String clock(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private boolean hasUsageAccess() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return true;
        AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (PinPadView.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
