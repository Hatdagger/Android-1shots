package com.swipekey.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Setup and settings: turn the keyboard on, pick a skin, try it out. */
public class SettingsActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences prefs;
    private float dp;
    private TextView step1, step2;
    private Button btn1, btn2;
    private KeyboardView preview;
    private final List<View> skinRows = new ArrayList<>();
    private Button t9Btn, qwertyBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        dp = getResources().getDisplayMetrics().density;
        Words.get(this); // start loading so the keyboard is ready sooner

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = px(16);
        col.setPadding(p, p, p, px(40));
        scroll.addView(col);

        TextView title = text("SwipeKey", 28, true);
        col.addView(title);
        col.addView(text("Swipe keyboard with the AIM phone skins, for every app.", 15, false));

        col.addView(header("Set up"));
        step1 = text("", 15, false);
        col.addView(step1);
        btn1 = button("1. Turn on SwipeKey", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        col.addView(btn1);
        step2 = text("", 15, false);
        col.addView(step2);
        btn2 = button("2. Switch to SwipeKey", new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });
        col.addView(btn2);

        EditText test = new EditText(this);
        test.setHint("Try it here");
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        test.setMinLines(2);
        col.addView(test, matchWrap(px(8)));

        col.addView(header("Skin"));
        col.addView(text("Tap ◐ on the keyboard to change skins while you type.", 13, false));
        preview = new KeyboardView(this);
        preview.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String code) { }
            @Override public void onLongKey(String code) { }
            @Override public void onSwipe(List<PointF> path) { }
            @Override public void onCandidate(int index) { }
            @Override public void onSkinPicked(String id) { prefs.edit().putString(Prefs.SKIN, id).apply(); preview.setPicker(false); }
            @Override public void onToggleLayout() { togglePhones(); }
            @Override public void onPress() { }
        });
        col.addView(preview, matchWrap(px(8)));

        for (final Skin s : Skin.ALL) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(8), px(8), px(8), px(8));
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(s.body);
            g.setCornerRadius(px(10));
            g.setStroke(px(3), s.ink);
            sw.setBackground(g);
            LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(px(44), px(44));
            swl.rightMargin = px(12);
            row.addView(sw, swl);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(text(s.name, 16, true));
            texts.addView(text(s.keysLabel() + " · " + s.blurb, 13, false));
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setTag(s.id);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.edit().putString(Prefs.SKIN, s.id).apply();
                }
            });
            skinRows.add(row);
            col.addView(row, matchWrap(px(2)));
        }

        col.addView(header("Phones type with"));
        col.addView(text("For the first seven phones and the plain SwipeKey skin. The other gadgets always use their own keys.", 13, false));
        LinearLayout kinds = new LinearLayout(this);
        t9Btn = button("Keypad (T9)", new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean(Prefs.PHONES_QWERTY, false).putBoolean(Prefs.PLAIN_T9, true).apply(); }
        });
        qwertyBtn = button("QWERTY", new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean(Prefs.PHONES_QWERTY, true).putBoolean(Prefs.PLAIN_T9, false).apply(); }
        });
        kinds.addView(t9Btn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        kinds.addView(qwertyBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(kinds);

        col.addView(header("Options"));
        col.addView(check("Swipe typing", Prefs.SWIPE, true));
        col.addView(check("Word suggestions", Prefs.SUGGEST, true));
        col.addView(check("T9 word guessing (off = multi-tap)", Prefs.PREDICTIVE, true));
        col.addView(check("Capital at the start of sentences", Prefs.AUTOCAP, true));
        col.addView(check("Flip / slide opening animation", Prefs.ANIMATE, true));
        col.addView(check("Key click sound", Prefs.SOUND, true));
        col.addView(check("Vibrate on key press", Prefs.VIBRATE, true));

        col.addView(header("Keyboard height"));
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        String h = prefs.getString(Prefs.HEIGHT, "m");
        String[][] sizes = {{"s", "Small"}, {"m", "Medium"}, {"l", "Large"}};
        for (final String[] sz : sizes) {
            RadioButton r = new RadioButton(this);
            r.setText(sz[1]);
            r.setId(View.generateViewId());
            r.setChecked(sz[0].equals(h));
            r.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                    if (on) prefs.edit().putString(Prefs.HEIGHT, sz[0]).apply();
                }
            });
            rg.addView(r);
        }
        col.addView(rg);

        col.addView(header("Tips"));
        col.addView(text(
                "• Swipe: slide across the letters without lifting. Tap a word in the strip if it guessed wrong; ⌫ right after a swipe deletes the whole word.\n"
                + "• Tap a suggestion to finish the word you're typing.\n"
                + "• Hold a top-row letter for its number. Double-tap space for \". \"\n"
                + "• Hold space to switch to another keyboard.\n"
                + "• T9: one press per letter, * for the next word, # changes Abc / abc / ABC / 123, hold # for multi-tap, hold a key for its number, 1 is punctuation, 0 is space.\n"
                + "• The T9 / ABC button in the strip swaps a phone between its keypad and QWERTY.", 14, false));

        setContentView(scroll);
        prefs.registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    @Override
    protected void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refresh(); // back from the keyboard picker
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) { refresh(); }

    private void togglePhones() {
        Skin s = Skin.byId(prefs.getString(Prefs.SKIN, "classic"));
        if (s.device == Skin.PLAIN) prefs.edit().putBoolean(Prefs.PLAIN_T9, !prefs.getBoolean(Prefs.PLAIN_T9, false)).apply();
        else prefs.edit().putBoolean(Prefs.PHONES_QWERTY, !prefs.getBoolean(Prefs.PHONES_QWERTY, false)).apply();
    }

    private void refresh() {
        boolean enabled = false, current = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null)
            for (InputMethodInfo i : imm.getEnabledInputMethodList())
                if (i.getPackageName().equals(getPackageName())) enabled = true;
        String def = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        current = def != null && def.startsWith(getPackageName() + "/");
        step1.setText(enabled ? "✓ SwipeKey is turned on." : "Turn SwipeKey on in your keyboard settings (Android may warn that keyboards can see what you type — SwipeKey has no internet access and keeps nothing but the words you use).");
        btn1.setEnabled(!enabled);
        step2.setText(current ? "✓ SwipeKey is your keyboard." : "Then pick SwipeKey as the keyboard you use.");
        btn2.setEnabled(enabled && !current);

        Skin s = Skin.byId(prefs.getString(Prefs.SKIN, "classic"));
        preview.setSkin(s);
        int kb = s.keyboard();
        boolean qwerty = s.device == Skin.PLAIN ? !prefs.getBoolean(Prefs.PLAIN_T9, false) : prefs.getBoolean(Prefs.PHONES_QWERTY, false);
        preview.setLayout(kb == Skin.KB_WIDE_T9 ? KeyboardView.L_WIDE_T9 : kb == Skin.KB_T9 ? KeyboardView.L_T9
                : kb == Skin.KB_QWERTY || qwerty ? KeyboardView.L_QWERTY : KeyboardView.L_T9);
        preview.setCandidates(null, s.name + " · " + s.keysLabel());
        String h = prefs.getString(Prefs.HEIGHT, "m");
        preview.setHeightScale("s".equals(h) ? 0.85f : "l".equals(h) ? 1.18f : 1f);
        preview.setAnimate(prefs.getBoolean(Prefs.ANIMATE, true));
        preview.open();
        for (View row : skinRows) {
            boolean sel = s.id.equals(row.getTag());
            row.setBackgroundColor(sel ? 0x332F7BFF : 0);
        }
        boolean phonesQ = prefs.getBoolean(Prefs.PHONES_QWERTY, false);
        t9Btn.setTypeface(null, phonesQ ? Typeface.NORMAL : Typeface.BOLD);
        qwertyBtn.setTypeface(null, phonesQ ? Typeface.BOLD : Typeface.NORMAL);
        t9Btn.setText((phonesQ ? "" : "✓ ") + "Keypad (T9)");
        qwertyBtn.setText((phonesQ ? "✓ " : "") + "QWERTY");
    }

    // ------------------------------------------------------------------ little view helpers

    private int px(int d) { return (int) (d * dp + 0.5f); }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = top;
        return l;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(2), 0, px(2));
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, px(20), 0, px(4));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private CheckBox check(String label, final String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(prefs.getBoolean(key, def));
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.edit().putBoolean(key, on).apply(); }
        });
        return c;
    }
}
