package com.hunik.kboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Opens from the app icon: step-by-step enabling, a test field and three options. */
public class SetupActivity extends Activity {

    private static final int C_ACCENT = 0xFF7A1F2B;
    private static final int C_TEXT = 0xFF1E1E1E;
    private static final int C_DIM = 0xFF6B6B6B;

    private TextView status1, status2;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView button(int label, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_ACCENT);
        g.setCornerRadius(dp(24));
        b.setBackground(g);
        b.setOnClickListener(l);
        return b;
    }

    private TextView note(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private CheckBox option(int label, final String key, boolean def) {
        final SharedPreferences p = getSharedPreferences("kboard", MODE_PRIVATE);
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextSize(15);
        c.setTextColor(C_TEXT);
        c.setChecked(p.getBoolean(key, def));
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                p.edit().putBoolean(key, checked).apply();
            }
        });
        return c;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(32), dp(24), dp(32));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        col.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView title = note(getString(R.string.app_name), 24, C_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(12), 0, 0);
        col.addView(title);
        col.addView(note(getString(R.string.tagline), 14, C_DIM));
        col.addView(note("v" + versionName(), 11, 0xFFA0A0A0));

        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(24);
        col.addView(button(R.string.step1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        }), bl);
        status1 = note("", 13, C_DIM);
        status1.setPadding(0, dp(6), 0, 0);
        col.addView(status1);

        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl2.topMargin = dp(16);
        col.addView(button(R.string.step2, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        }), bl2);
        status2 = note("", 13, C_DIM);
        status2.setPadding(0, dp(6), 0, 0);
        col.addView(status2);

        EditText test = new EditText(this);
        test.setHint(R.string.test_hint);
        test.setMinLines(2);
        test.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(20);
        col.addView(test, tl);

        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ol.topMargin = dp(8);
        col.addView(option(R.string.opt_haptic, "haptic", true), ol);
        col.addView(option(R.string.opt_preview, "preview", true), ol);
        col.addView(option(R.string.opt_sound, "sound", false), ol);

        TextView hint = note(getString(R.string.hint_long), 12, C_DIM);
        hint.setPadding(0, dp(16), 0, 0);
        col.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        String def = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean en = enabled != null && enabled.contains(getPackageName());
        boolean sel = def != null && def.startsWith(getPackageName());
        status1.setText(en ? R.string.st_enabled : R.string.st_disabled);
        status1.setTextColor(en ? 0xFF2E7D32 : C_DIM);
        status2.setText(sel ? R.string.st_selected : R.string.st_not_selected);
        status2.setTextColor(sel ? 0xFF2E7D32 : C_DIM);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
