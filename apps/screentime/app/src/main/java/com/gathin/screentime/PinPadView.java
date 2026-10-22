package com.gathin.screentime;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A full-screen PIN overlay that works with both the remote's number keys and an on-screen keypad
 * driven by the D-pad. Reused by the Home screen (unlock / open settings), the lock screen
 * (continue), and settings (change PIN).
 *
 * Show it with {@link #show}. The host activity must forward key events via {@link #handleKey} from
 * its dispatchKeyEvent so the remote's number buttons reach the pad.
 */
public class PinPadView extends FrameLayout {

    enum Mode { VERIFY, SET }

    interface Callback {
        void onDone(boolean success);
    }

    private static PinPadView current;   // at most one pad on screen; routes hardware number keys

    private final Prefs prefs;
    private final Mode mode;
    private final Callback cb;
    private final TextView title;
    private final TextView dots;
    private final TextView error;

    private final StringBuilder entered = new StringBuilder();
    private String firstEntry = null;    // SET mode: the first of the two matching entries

    private PinPadView(Activity host, CharSequence titleText, Mode mode, Prefs prefs, Callback cb) {
        super(host);
        this.prefs = prefs;
        this.mode = mode;
        this.cb = cb;

        setBackgroundColor(0xE6000000);
        setClickable(true);
        setFocusable(true);

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        addView(card, clp);

        title = new TextView(host);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView hint = new TextView(host);
        hint.setText(R.string.pin_hint_remote);
        hint.setTextColor(0xFF9AA0AA);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, dp(14));
        card.addView(hint);

        dots = new TextView(host);
        dots.setTextColor(Color.WHITE);
        dots.setTextSize(34);
        dots.setGravity(Gravity.CENTER);
        card.addView(dots);
        refreshDots();

        error = new TextView(host);
        error.setTextColor(0xFFFF6B6B);
        error.setTextSize(16);
        error.setGravity(Gravity.CENTER);
        error.setPadding(0, dp(8), 0, dp(8));
        card.addView(error);

        card.addView(buildGrid(host));
    }

    private GridLayout buildGrid(Context c) {
        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        // 1-9, then delete / 0 / OK
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        View first = null;
        for (String k : keys) {
            TextView b = new TextView(c);
            b.setText(k);
            b.setTextColor(Color.WHITE);
            b.setTextSize(22);
            b.setGravity(Gravity.CENTER);
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setBackgroundResource(R.drawable.key_bg);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(96);
            lp.height = dp(68);
            lp.setMargins(dp(7), dp(7), dp(7), dp(7));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> onKey(k));
            if (first == null) first = b;
            grid.addView(b);
        }
        if (first != null) {
            final View f = first;
            post(f::requestFocus);
        }
        return grid;
    }

    private void onKey(String k) {
        if ("OK".equals(k)) {
            if (entered.length() == 4) submit();
        } else if ("⌫".equals(k)) {
            backspace();
        } else {
            pushDigit(k.charAt(0));
        }
    }

    private void pushDigit(char d) {
        if (entered.length() >= 4) return;
        entered.append(d);
        refreshDots();
        if (entered.length() == 4) submit();
    }

    private void backspace() {
        if (entered.length() > 0) {
            entered.setLength(entered.length() - 1);
            refreshDots();
        }
    }

    private void refreshDots() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            s.append(i < entered.length() ? "●" : "○");
            if (i < 3) s.append("   ");
        }
        dots.setText(s);
    }

    private void submit() {
        String code = entered.toString();
        entered.setLength(0);
        refreshDots();
        if (mode == Mode.VERIFY) {
            if (prefs.checkPin(code)) {
                done(true);
            } else {
                error.setText(R.string.pin_wrong);
            }
            return;
        }
        // SET: two matching entries
        if (firstEntry == null) {
            firstEntry = code;
            error.setText("");
            title.setText(R.string.confirm_pin);
        } else if (firstEntry.equals(code)) {
            prefs.setPin(code);
            done(true);
        } else {
            firstEntry = null;
            error.setText(R.string.pin_mismatch);
            title.setText(R.string.set_pin);
        }
    }

    private void done(boolean success) {
        if (current == this) current = null;
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
        if (cb != null) cb.onDone(success);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    // ---- key routing from the host activity ------------------------------------------------------

    /** Called from the host's dispatchKeyEvent. Returns true when the pad consumed the key. */
    static boolean handleKey(KeyEvent e) {
        PinPadView pad = current;
        if (pad == null) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            // Swallow the matching UP too, so it never leaks to the app behind the overlay.
            int c = e.getKeyCode();
            return isDigit(c) || c == KeyEvent.KEYCODE_DEL || c == KeyEvent.KEYCODE_BACK;
        }
        int c = e.getKeyCode();
        if (isDigit(c)) {
            pad.pushDigit((char) ('0' + (c - KeyEvent.KEYCODE_0)));
            return true;
        }
        if (c == KeyEvent.KEYCODE_DEL) {
            pad.backspace();
            return true;
        }
        if (c == KeyEvent.KEYCODE_BACK) {
            pad.done(false);
            return true;
        }
        return false;   // let D-pad / center fall through to the on-screen keys
    }

    private static boolean isDigit(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9;
    }

    /** Add a pad over the activity's content and route keys to it. */
    static void show(Activity host, CharSequence title, Mode mode, Prefs prefs, Callback cb) {
        PinPadView pad = new PinPadView(host, title, mode, prefs, cb);
        current = pad;
        host.addContentView(pad, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pad.requestFocus();
    }

    static boolean isShowing() {
        return current != null;
    }
}
