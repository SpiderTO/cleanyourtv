package com.gathin.screentime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The full-screen block shown over a counted app when the daily limit is reached or the curfew hour
 * has passed. The only way back into the app is the parent PIN (which switches to parent mode);
 * anything else sends the child to the Home screen.
 */
public class LockActivity extends Activity {
    static final String EXTRA_REASON = "reason";
    static final String REASON_TIME_UP = "time_up";
    static final String REASON_CURFEW = "curfew";

    private Prefs prefs;
    private LinearLayout column;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF000000);
        int pad = dp(48);
        root.setPadding(pad, pad, pad, pad);
        column = root;
        setContentView(root);

        render();
    }

    private void render() {
        column.removeAllViews();
        boolean curfew = REASON_CURFEW.equals(getIntent().getStringExtra(EXTRA_REASON));

        TextView title = new TextView(this);
        title.setText(curfew ? R.string.curfew_title : R.string.time_up_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(52);
        title.setGravity(Gravity.CENTER);
        column.addView(title);

        TextView body = new TextView(this);
        body.setText(curfew ? getString(R.string.curfew_body, prefs.curfewHour())
                : getString(R.string.time_up_body));
        body.setTextColor(0xFFC9CDD4);
        body.setTextSize(20);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(16), 0, dp(32));
        column.addView(body);

        addButton(getString(R.string.enter_pin_to_continue), true, () ->
                PinPadView.show(this, getString(R.string.enter_pin), PinPadView.Mode.VERIFY, prefs, ok -> {
                    if (ok) {
                        AppState.unlock();
                        finish();   // reveal the app behind, now allowed in parent mode
                    } else {
                        render();
                    }
                }));

        addButton(getString(R.string.back_home), false, this::goHome);
    }

    private void addButton(String label, boolean focusFirst, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setBackgroundResource(R.drawable.key_bg);
        int px = dp(16);
        b.setPadding(px, px, px, px);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        column.addView(b);
        if (focusFirst) b.post(b::requestFocus);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    public void onBackPressed() {
        goHome();   // never fall back into the counted app
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (PinPadView.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
