package com.gathin.screentime;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parent settings, reached only after the PIN. Changes the PIN, the daily minutes, the curfew window,
 * which apps are counted, and strict mode with its allow list. Everything is typed with the remote's number keys or toggled with the
 * D-pad, so no touch is needed.
 */
public class SettingsActivity extends Activity {
    private Prefs prefs;
    private LinearLayout column;
    private EditText limitField;
    private EditText curfewField;
    private EditText curfewEndField;
    private CheckBox strictCheck;
    private final Map<String, CheckBox> appChecks = new LinkedHashMap<>();
    private final Map<String, CheckBox> allowChecks = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFF0B0B0F);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(40);
        column.setPadding(pad, pad, pad, pad);
        root.addView(column);
        setContentView(root);

        // Gate on the PIN even if this screen is started directly (e.g. over ADB).
        if (prefs.hasPin()) {
            PinPadView.show(this, getString(R.string.enter_pin), PinPadView.Mode.VERIFY, prefs, ok -> {
                if (ok) buildForm();
                else finish();
            });
        } else {
            buildForm();
        }
    }

    private void buildForm() {
        column.removeAllViews();
        addTitle(getString(R.string.settings_title));

        addLabel(getString(R.string.daily_limit_label));
        limitField = addNumberField(String.valueOf(prefs.limitMinutes()));

        addLabel(getString(R.string.curfew_label));
        curfewField = addNumberField(String.valueOf(prefs.curfewHour()));
        addLabel(getString(R.string.curfew_end_label));
        curfewEndField = addNumberField(String.valueOf(prefs.curfewEndHour()));

        addGap();
        addButton(getString(R.string.change_pin), () ->
                PinPadView.show(this, getString(R.string.set_pin), PinPadView.Mode.SET, prefs, ok ->
                        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()));

        addGap();
        addLabel(getString(R.string.counted_apps_label));
        Map<String, String> apps = launchableApps();
        Set<String> counted = prefs.countedApps();
        for (Map.Entry<String, String> app : apps.entrySet()) {
            appChecks.put(app.getKey(), addCheck(app.getValue(), counted.contains(app.getKey())));
        }

        addGap();
        strictCheck = addCheck(getString(R.string.strict_label), prefs.strictWhenBlocked());
        addLabel(getString(R.string.allowed_apps_label));
        Set<String> allowed = prefs.allowedWhenBlocked();
        for (Map.Entry<String, String> app : apps.entrySet()) {
            allowChecks.put(app.getKey(), addCheck(app.getValue(), allowed.contains(app.getKey())));
        }

        addGap();
        addButton(getString(R.string.save), this::save);
    }

    private void save() {
        prefs.setLimitMinutes(parseInt(limitField, prefs.limitMinutes()));
        prefs.setCurfewHour(clampHour(parseInt(curfewField, prefs.curfewHour())));
        prefs.setCurfewEndHour(clampHour(parseInt(curfewEndField, prefs.curfewEndHour())));
        prefs.setCountedApps(checked(appChecks));
        prefs.setStrictWhenBlocked(strictCheck.isChecked());
        prefs.setAllowedWhenBlocked(checked(allowChecks));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static Set<String> checked(Map<String, CheckBox> checks) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, CheckBox> e : checks.entrySet()) {
            if (e.getValue().isChecked()) out.add(e.getKey());
        }
        return out;
    }

    /** Package -> label for every launchable app on the TV (leanback or ordinary), minus ourselves. */
    private Map<String, String> launchableApps() {
        PackageManager pm = getPackageManager();
        Map<String, String> out = new LinkedHashMap<>();
        List<ResolveInfo> found = new ArrayList<>();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent normal = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        found.addAll(pm.queryIntentActivities(leanback, 0));
        found.addAll(pm.queryIntentActivities(normal, 0));
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || out.containsKey(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            out.put(pkg, label != null ? label.toString() : pkg);
        }
        // Make sure the defaults are always offered even if not currently launchable-visible.
        for (String def : new String[]{Prefs.YOUTUBE, Prefs.YOUTUBE_KIDS, Prefs.DEFAULT_ALLOWED[0]}) {
            if (!out.containsKey(def)) out.put(def, labelOf(def));
        }
        return out;
    }

    private String labelOf(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // ---- view helpers ----------------------------------------------------------------------------
    private void addTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(28);
        t.setPadding(0, 0, 0, dp(16));
        column.addView(t);
    }

    private void addLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFF9AA0AA);
        t.setTextSize(15);
        t.setPadding(0, dp(12), 0, dp(4));
        column.addView(t);
    }

    private CheckBox addCheck(String label, boolean on) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(Color.WHITE);
        cb.setTextSize(16);
        cb.setChecked(on);
        cb.setPadding(dp(8), dp(10), dp(8), dp(10));
        column.addView(cb);
        return cb;
    }

    private EditText addNumberField(String value) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setTextSize(20);
        e.setBackgroundResource(R.drawable.key_bg);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        e.setLayoutParams(lp);
        column.addView(e);
        return e;
    }

    private void addButton(String label, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setFocusable(true);
        b.setBackgroundResource(R.drawable.key_bg);
        int px = dp(16);
        b.setPadding(px, px, px, px);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        column.addView(b);
    }

    private void addGap() {
        android.view.View v = new android.view.View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)));
        column.addView(v);
    }

    private int parseInt(EditText e, int fallback) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int clampHour(int h) {
        return Math.max(0, Math.min(23, h));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (PinPadView.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
