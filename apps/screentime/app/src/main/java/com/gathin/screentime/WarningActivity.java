package com.gathin.screentime;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A big, full-screen "X minutes left" heads-up shown a few minutes before the daily limit. It is our
 * own activity (so the TV can screenshot it, unlike a Toast), stays on screen for a fixed time over a
 * dimmed view of the app the child is watching, then finishes and returns to that app. Purely a
 * warning: it never blocks, so the child keeps their remaining minutes.
 */
public class WarningActivity extends Activity {
    static final String EXTRA_MINUTES = "minutes";
    private static final long SHOW_MS = 10_000;   // stays 10 seconds, then returns to the app

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int minutes = getIntent().getIntExtra(EXTRA_MINUTES, 5);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xCC0B0B0F);   // translucent: the app behind shows faintly
        int pad = dp(48);
        root.setPadding(pad, pad, pad, pad);

        TextView big = new TextView(this);
        big.setText(getString(R.string.warn_minutes_left, minutes));
        big.setTextColor(Color.WHITE);
        big.setTextSize(60);
        big.setGravity(Gravity.CENTER);
        root.addView(big);

        TextView sub = new TextView(this);
        sub.setText(R.string.warn_sub);
        sub.setTextColor(0xFFC9CDD4);
        sub.setTextSize(24);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(18), 0, 0);
        root.addView(sub);

        setContentView(root);
        handler.postDelayed(this::finish, SHOW_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    /** Swallow remote keys so they neither leak to the app behind nor dismiss the warning early. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return true;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
