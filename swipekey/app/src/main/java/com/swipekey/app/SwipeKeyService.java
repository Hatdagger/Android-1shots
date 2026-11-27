package com.swipekey.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;

/** The system keyboard: AIM phone skins with T9, QWERTY and swipe typing. */
public class SwipeKeyService extends InputMethodService
        implements KeyboardView.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    private KeyboardView view;
    private Words words;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // settings
    private Skin skin;
    private boolean phonesQwerty, sound, vibrate, swipe, suggest, autocap, predictive, anim;

    // field
    private boolean password, noSuggest, numberField;
    private int enterAction = EditorInfo.IME_ACTION_NONE;

    // qwerty
    private int shift; // 0 off, 1 next letter, 2 caps lock
    private long lastShiftTap;
    private long lastSpace;
    private boolean justSwiped;
    private String lastSwipe;
    private final List<String> swipeAlts = new ArrayList<>();

    // T9
    private static final int CASE_SENTENCE = 0, CASE_LOWER = 1, CASE_UPPER = 2, CASE_NUM = 3;
    private int caseMode = CASE_SENTENCE;
    private final StringBuilder keys = new StringBuilder();
    private List<String> t9Words = new ArrayList<>();
    private int t9Index;
    private boolean t9Exact;
    private String composing = ""; // shown text (T9 word, or multi-tap letter)
    private String fallback = "";  // letters typed when no dictionary word fits
    private boolean capWord;       // the word being composed starts a sentence
    private char tapKey;           // multi-tap / punctuation key in progress
    private int tapCount;
    private long tapTime;
    private static final String PUNCT = ".,?!'-@:;()/\"&1";
    private static final String[] LETTERS = {" 0", PUNCT, "abc2", "def3", "ghi4", "jkl5", "mno6", "pqrs7", "tuv8", "wxyz9"};
    private final Runnable tapDone = new Runnable() {
        @Override public void run() { if (tapKey != 0) commitComposing(); }
    };

    private Vibrator vibrator;
    private AudioManager audio;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Prefs.get(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        words = Words.get(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        readPrefs();
    }

    @Override
    public void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        readPrefs();
        if (view != null) applyToView();
    }

    private void readPrefs() {
        skin = Skin.byId(prefs.getString(Prefs.SKIN, "classic"));
        phonesQwerty = prefs.getBoolean(Prefs.PHONES_QWERTY, false);
        sound = prefs.getBoolean(Prefs.SOUND, true);
        vibrate = prefs.getBoolean(Prefs.VIBRATE, true);
        swipe = prefs.getBoolean(Prefs.SWIPE, true);
        suggest = prefs.getBoolean(Prefs.SUGGEST, true);
        autocap = prefs.getBoolean(Prefs.AUTOCAP, true);
        predictive = prefs.getBoolean(Prefs.PREDICTIVE, true);
        anim = prefs.getBoolean(Prefs.ANIMATE, true);
    }

    @Override
    public View onCreateInputView() {
        view = new KeyboardView(this);
        view.setListener(this);
        applyToView();
        return view;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // keep the app visible above the keyboard, even in landscape
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (view == null) return;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        numberField = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME;
        password = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        noSuggest = password || numberField || var == InputType.TYPE_TEXT_VARIATION_URI
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || (info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 && cls != InputType.TYPE_CLASS_TEXT;
        enterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if ((info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                && enterAction != EditorInfo.IME_ACTION_SEND) enterAction = EditorInfo.IME_ACTION_NONE;
        resetT9();
        justSwiped = false;
        lastSwipe = null;
        shift = 0;
        caseMode = numberField ? CASE_NUM : CASE_SENTENCE;
        applyToView();
        view.open();
        updateShift();
        refresh();
    }

    @Override
    public void onFinishInputView(boolean finishing) {
        commitComposing();
        if (view != null) view.setPicker(false);
        super.onFinishInputView(finishing);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        // The user moved the cursor away from the word being built: keep what's there.
        if (!composing.isEmpty() && (candStart == -1 || newSelStart != candEnd || newSelEnd != candEnd)) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            resetT9();
            refresh();
        } else if (composing.isEmpty() && view != null && candStart == -1) {
            updateShift();
            refresh();
        }
    }

    private int layoutFor() {
        if (numberField) return KeyboardView.L_T9;
        switch (skin.keyboard()) {
            case Skin.KB_WIDE_T9: return KeyboardView.L_WIDE_T9;
            case Skin.KB_T9: return KeyboardView.L_T9;
            case Skin.KB_QWERTY: return KeyboardView.L_QWERTY;
            default:
                boolean qwerty = skin.device == Skin.PLAIN ? !prefs.getBoolean(Prefs.PLAIN_T9, false) : phonesQwerty;
                return qwerty ? KeyboardView.L_QWERTY : KeyboardView.L_T9;
        }
    }

    private void applyToView() {
        if (view == null) return;
        view.setSkin(skin);
        int l = layoutFor();
        if (!(l == KeyboardView.L_QWERTY && view.getLayout() == KeyboardView.L_SYMBOLS)) view.setLayout(l);
        view.setSwipe(swipe && !password);
        view.setAnimate(anim);
        String h = prefs.getString(Prefs.HEIGHT, "m");
        view.setHeightScale("s".equals(h) ? 0.85f : "l".equals(h) ? 1.18f : 1f);
        view.setEnterLabel(enterLabel());
        view.setT9Mode(modeLabel(), predictive && !password);
        view.setShift(shift);
    }

    private String enterLabel() {
        switch (enterAction) {
            case EditorInfo.IME_ACTION_SEND: return "Send";
            case EditorInfo.IME_ACTION_GO: return "Go";
            case EditorInfo.IME_ACTION_SEARCH: return "Search";
            case EditorInfo.IME_ACTION_DONE: return "Done";
            case EditorInfo.IME_ACTION_NEXT: return "Next";
            default: return "⏎";
        }
    }

    private boolean t9Layout() {
        int l = view == null ? -1 : view.getLayout();
        return l == KeyboardView.L_T9 || l == KeyboardView.L_WIDE_T9;
    }

    // ------------------------------------------------------------------ feedback

    @Override
    public void onPress() {
        if (vibrate && vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(14, 90));
                else vibrator.vibrate(14);
            } catch (Exception ignored) { }
        }
        if (sound && audio != null) audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1);
    }

    // ------------------------------------------------------------------ keys

    @Override
    public void onKey(String code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (t9Layout() && code.length() == 1 && (Character.isDigit(code.charAt(0)) || code.equals("*") || code.equals("#"))) {
            t9Press(code.charAt(0));
            return;
        }
        switch (code) {
            case "SHIFT": {
                long now = SystemClock.uptimeMillis();
                if (shift == 2) shift = 0;
                else if (shift == 1 && now - lastShiftTap < 400) shift = 2;
                else shift = shift == 0 ? 1 : 0;
                lastShiftTap = now;
                view.setShift(shift);
                return;
            }
            case "SYM": view.setLayout(KeyboardView.L_SYMBOLS); return;
            case "ABC": view.setLayout(KeyboardView.L_QWERTY); return;
            case "DEL": backspace(ic); return;
            case "SPACE": space(ic); return;
            case "ENTER": enter(ic); return;
        }
        // a letter or symbol
        commitComposing();
        String s = code;
        boolean letter = Character.isLetter(s.charAt(0));
        if (letter && justSwiped) ic.commitText(" ", 1);
        justSwiped = false;
        lastSwipe = null;
        if (letter && shift > 0) s = s.toUpperCase();
        if (!letter && isWordEnd(s)) learnWordBeforeCursor(ic);
        // "word ." -> "word." when the space came from a swipe or a suggestion
        if (".,?!:;".contains(s)) {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (before != null && before.length() == 2 && before.charAt(1) == ' ' && Character.isLetter(before.charAt(0))
                    && autoSpaced) ic.deleteSurroundingText(1, 0);
        }
        autoSpaced = false;
        ic.commitText(s, 1);
        if (shift == 1 && letter) shift = 0;
        updateShift();
        refresh();
    }

    private boolean autoSpaced;

    private static boolean isWordEnd(String s) { return " .,?!:;\n".contains(s); }

    @Override
    public void onLongKey(String code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        onPress();
        if (t9Layout()) {
            char c = code.length() == 1 ? code.charAt(0) : 0;
            if (c == '#') {
                if (caseMode == CASE_NUM) { commitComposing(); ic.commitText("#", 1); return; }
                predictive = !predictive;
                prefs.edit().putBoolean(Prefs.PREDICTIVE, predictive).apply();
                commitComposing();
                view.flash(predictive ? "T9 word guessing on" : "Multi-tap (press a key until the letter shows)");
                return;
            }
            if (caseMode == CASE_NUM && (c == '0' || c == '*' || c == '1')) {
                // number boxes: hold 0 for +, * for a decimal point, 1 for minus
                ic.commitText(c == '0' ? "+" : c == '*' ? "." : "-", 1);
                return;
            }
            if (Character.isDigit(c) || c == '*') { commitComposing(); ic.commitText(String.valueOf(c), 1); refresh(); return; }
        }
        if ("SPACE".equals(code)) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
            return;
        }
        if ("SHIFT".equals(code)) { shift = 2; view.setShift(2); return; }
        if (code.length() == 1) {
            // top row: long-press for the number shown in the corner
            int i = "qwertyuiop".indexOf(code.charAt(0));
            String s = i >= 0 ? String.valueOf("1234567890".charAt(i)) : code;
            if (code.equals(".")) s = "…";
            else if (code.equals(",")) s = "!";
            if (justSwiped && Character.isDigit(s.charAt(0))) ic.commitText(" ", 1);
            justSwiped = false;
            ic.commitText(s, 1);
            refresh();
            return;
        }
        onKey(code);
    }

    private void backspace(InputConnection ic) {
        if (!composing.isEmpty()) {
            if (tapKey != 0) {
                handler.removeCallbacks(tapDone);
                tapKey = 0;
                composing = "";
                ic.setComposingText("", 1);
                ic.finishComposingText();
            } else if (keys.length() > 0) {
                keys.setLength(keys.length() - 1);
                if (fallback.length() > 0) fallback = fallback.substring(0, fallback.length() - 1);
                if (keys.length() == 0) {
                    composing = "";
                    ic.setComposingText("", 1);
                    ic.finishComposingText();
                } else lookUp(ic);
            }
            refresh();
            return;
        }
        if (lastSwipe != null && justSwiped) {
            // undo the whole swiped word in one go
            CharSequence before = ic.getTextBeforeCursor(lastSwipe.length(), 0);
            if (before != null && before.toString().equals(lastSwipe)) {
                ic.deleteSurroundingText(lastSwipe.length(), 0);
                lastSwipe = null;
                justSwiped = false;
                updateShift();
                refresh();
                return;
            }
        }
        justSwiped = false;
        lastSwipe = null;
        autoSpaced = false;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
        updateShift();
        refresh();
    }

    private void space(InputConnection ic) {
        boolean hadWord = !composing.isEmpty();
        commitComposing();
        long now = SystemClock.uptimeMillis();
        justSwiped = false;
        lastSwipe = null;
        if (!hadWord) learnWordBeforeCursor(ic);
        // double space -> ". "
        if (!password && now - lastSpace < 500) {
            CharSequence b = ic.getTextBeforeCursor(2, 0);
            if (b != null && b.length() == 2 && b.charAt(1) == ' ' && Character.isLetterOrDigit(b.charAt(0))) {
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                lastSpace = 0;
                updateShift();
                refresh();
                return;
            }
        }
        lastSpace = now;
        autoSpaced = false;
        ic.commitText(" ", 1);
        updateShift();
        refresh();
    }

    private void enter(InputConnection ic) {
        commitComposing();
        learnWordBeforeCursor(ic);
        justSwiped = false;
        lastSwipe = null;
        if (enterAction != EditorInfo.IME_ACTION_NONE && enterAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(enterAction);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
        updateShift();
        refresh();
    }

    // ------------------------------------------------------------------ swipe

    @Override
    public void onSwipe(List<PointF> path) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        List<String> found = SwipeDecoder.decode(path, view.letterCentres(), view.keyWidth(), view.keyHeight(), words);
        if (found.isEmpty()) {
            view.flash(words.ready() ? "No word found — try again" : "Loading words…");
            return;
        }
        commitComposing();
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() == 1 && !Character.isWhitespace(before.charAt(0))
                && "([{\"'/@#".indexOf(before.charAt(0)) < 0) {
            ic.commitText(" ", 1);
        }
        boolean cap = shift > 0;
        swipeAlts.clear();
        for (String w : found) swipeAlts.add(shape(w, shift == 2, cap));
        String w = swipeAlts.get(0);
        ic.commitText(w, 1);
        words.learn(found.get(0));
        lastSwipe = w;
        justSwiped = true;
        autoSpaced = false;
        if (shift == 1) shift = 0;
        view.setShift(shift);
        view.setCandidates(swipeAlts.size() > 1 ? swipeAlts.subList(1, swipeAlts.size()) : swipeAlts, "");
    }

    private static String shape(String w, boolean upper, boolean cap) {
        if (upper) return w.toUpperCase();
        if (w.equals("i") || w.startsWith("i'")) w = "I" + w.substring(1);
        if (cap && w.length() > 0) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
        return w;
    }

    // ------------------------------------------------------------------ suggestions

    @Override
    public void onCandidate(int index) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!composing.isEmpty() && tapKey == 0 && keys.length() > 0) {
            // T9: pick a word from the strip
            List<String> shown = t9Shown();
            if (index < shown.size()) {
                String w = shown.get(index);
                ic.setComposingText(w, 1);
                ic.finishComposingText();
                words.learn(w);
                resetT9();
                ic.commitText(" ", 1);
                updateShift();
                refresh();
            }
            return;
        }
        if (justSwiped && lastSwipe != null) {
            List<String> alts = swipeAlts.size() > 1 ? swipeAlts.subList(1, swipeAlts.size()) : swipeAlts;
            if (index >= alts.size()) return;
            String alt = alts.get(index);
            CharSequence before = ic.getTextBeforeCursor(lastSwipe.length(), 0);
            if (before != null && before.toString().equals(lastSwipe)) {
                ic.deleteSurroundingText(lastSwipe.length(), 0);
                ic.commitText(alt, 1);
                words.learn(alt.toLowerCase());
                // put the old pick in the strip so it can be chosen back
                List<String> next = new ArrayList<>();
                next.add(lastSwipe);
                for (String s : alts) if (!s.equals(alt)) next.add(s);
                swipeAlts.clear();
                swipeAlts.add(alt);
                swipeAlts.addAll(next);
                lastSwipe = alt;
                view.setCandidates(next, "");
            }
            return;
        }
        // completing the word being typed on QWERTY
        String prefix = wordBeforeCursor(ic);
        List<String> c = completions(prefix);
        if (index >= c.size()) return;
        String w = c.get(index);
        ic.deleteSurroundingText(prefix.length(), 0);
        ic.commitText(w + " ", 1);
        words.learn(w.toLowerCase());
        autoSpaced = true;
        if (shift == 1) shift = 0;
        updateShift();
        refresh();
    }

    private String wordBeforeCursor(InputConnection ic) {
        CharSequence b = ic.getTextBeforeCursor(40, 0);
        if (b == null) return "";
        int i = b.length();
        while (i > 0 && (Character.isLetter(b.charAt(i - 1)) || b.charAt(i - 1) == '\'')) i--;
        return b.subSequence(i, b.length()).toString();
    }

    private void learnWordBeforeCursor(InputConnection ic) {
        if (password || noSuggest) return;
        String w = wordBeforeCursor(ic);
        if (w.length() >= 2) words.learn(w.toLowerCase());
    }

    private List<String> completions(String prefix) {
        List<String> out = new ArrayList<>();
        if (prefix.isEmpty()) return out;
        boolean upper = prefix.length() > 1 && prefix.equals(prefix.toUpperCase());
        boolean cap = Character.isUpperCase(prefix.charAt(0));
        for (String w : words.complete(prefix, 3)) out.add(shape(w, upper, cap));
        return out;
    }

    /** Redraw the strip for whatever is being typed now. */
    private void refresh() {
        if (view == null) return;
        if (t9Layout()) {
            view.setT9Mode(modeLabel(), predictive && !password);
            if (!composing.isEmpty() && tapKey == 0 && keys.length() > 0) view.setCandidates(t9Shown(), "");
            else view.setCandidates(null, t9Hint());
            return;
        }
        if (justSwiped) return; // keep the swipe alternatives up
        InputConnection ic = getCurrentInputConnection();
        if (suggest && !noSuggest && ic != null) {
            String prefix = wordBeforeCursor(ic);
            List<String> c = completions(prefix);
            if (!c.isEmpty()) { view.setCandidates(c, ""); return; }
        }
        view.setCandidates(null, qwertyHint());
    }

    private String qwertyHint() {
        if (!words.ready()) return "Loading words…";
        if (password) return "Password: swipe is off";
        return swipe ? "Swipe across letters to type a word" : "";
    }

    private String t9Hint() {
        if (caseMode == CASE_NUM) return numberField ? "Hold 0 for + · * for . · 1 for −" : "123 · # for letters";
        if (!predictive || password) return "Tap a key until your letter shows · hold # for T9";
        return "One press per letter · * next word · # Abc/abc/ABC/123";
    }

    @Override
    public void onSkinPicked(String id) {
        commitComposing();
        prefs.edit().putString(Prefs.SKIN, id).apply();
        // prefs listener applies it; show the opening again so you see the gadget
        view.setPicker(false);
        view.open();
        refresh();
    }

    @Override
    public void onToggleLayout() {
        commitComposing();
        if (skin.device == Skin.PLAIN) {
            boolean t9 = view.getLayout() != KeyboardView.L_T9;
            prefs.edit().putBoolean(Prefs.PLAIN_T9, t9).apply();
        } else {
            prefs.edit().putBoolean(Prefs.PHONES_QWERTY, view.getLayout() == KeyboardView.L_T9).apply();
        }
        view.open();
        updateShift();
        refresh();
    }

    // ------------------------------------------------------------------ shift / capitals

    private boolean sentenceStart() {
        if (!autocap || password || noSuggest) return false;
        EditorInfo info = getCurrentInputEditorInfo();
        if (info != null && (info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence b = ic.getTextBeforeCursor(3, 0);
        if (b == null) return false;
        String s = b.toString();
        if (s.isEmpty()) return true;
        int i = s.length() - 1;
        if (s.charAt(i) == '\n') return true;
        if (s.charAt(i) != ' ') return false;
        while (i >= 0 && s.charAt(i) == ' ') i--;
        if (i < 0) return s.length() < 3; // only spaces before (start of the box)
        char c = s.charAt(i);
        return c == '.' || c == '!' || c == '?' || c == '\n';
    }

    private void updateShift() {
        if (shift == 2 || view == null) return;
        shift = sentenceStart() ? 1 : 0;
        view.setShift(shift);
    }

    // ------------------------------------------------------------------ T9

    private String modeLabel() {
        switch (caseMode) {
            case CASE_LOWER: return "abc";
            case CASE_UPPER: return "ABC";
            case CASE_NUM: return "123";
            default: return "Abc";
        }
    }

    private void resetT9() {
        handler.removeCallbacks(tapDone);
        keys.setLength(0);
        t9Words = new ArrayList<>();
        t9Index = 0;
        composing = "";
        fallback = "";
        tapKey = 0;
        tapCount = 0;
    }

    /** Put the word being built into the text box for good. */
    private void commitComposing() {
        InputConnection ic = getCurrentInputConnection();
        if (composing.isEmpty()) { resetT9(); return; }
        if (ic != null) {
            ic.setComposingText(composing, 1);
            ic.finishComposingText();
        }
        if (tapKey == 0 && keys.length() > 0 && t9Exact) words.learn(composing.toLowerCase());
        resetT9();
    }

    private boolean capsNow() {
        if (caseMode == CASE_UPPER) return true;
        return caseMode == CASE_SENTENCE && sentenceStart();
    }

    private String caseT9(String w) {
        if (caseMode == CASE_UPPER) return w.toUpperCase();
        if (caseMode == CASE_LOWER) return w;
        return shape(w, false, capWord);
    }

    private void t9Press(char k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        justSwiped = false;
        lastSwipe = null;
        long now = SystemClock.uptimeMillis();

        if (k == '#') {
            commitComposing();
            caseMode = numberField ? CASE_NUM : (caseMode + 1) % 4;
            refresh();
            return;
        }
        if (caseMode == CASE_NUM) {
            commitComposing();
            ic.commitText(String.valueOf(k), 1);
            refresh();
            return;
        }
        if (k == '*') {
            if (!composing.isEmpty() && tapKey == 0 && t9Words.size() > 1) {
                t9Index = (t9Index + 1) % t9Words.size();
                composing = caseT9(t9Words.get(t9Index));
                ic.setComposingText(composing, 1);
            } else if (composing.isEmpty()) {
                ic.commitText("*", 1);
            }
            refresh();
            return;
        }
        if (k == '0') {
            commitComposing();
            space(ic);
            return;
        }

        boolean multitap = k == '1' || !predictive || password;
        if (multitap) {
            String letters = LETTERS[k - '0'];
            if (tapKey == k && now - tapTime < 900) {
                tapCount = (tapCount + 1) % letters.length();
            } else {
                commitComposing();
                tapKey = k;
                tapCount = 0;
                capWord = capsNow();
            }
            tapTime = now;
            String ch = String.valueOf(letters.charAt(tapCount));
            if (k != '1') ch = caseMode == CASE_UPPER || capWord && caseMode == CASE_SENTENCE ? ch.toUpperCase() : ch;
            composing = ch;
            ic.setComposingText(ch, 1);
            handler.removeCallbacks(tapDone);
            handler.postDelayed(tapDone, 900);
            refresh();
            return;
        }

        // predictive: one press per letter
        if (tapKey != 0) commitComposing();
        if (keys.length() == 0) capWord = capsNow();
        keys.append(k);
        fallback = fallback + LETTERS[k - '0'].charAt(0);
        lookUp(ic);
        refresh();
    }

    private void lookUp(InputConnection ic) {
        boolean[] exact = new boolean[1];
        t9Words = words.t9(keys.toString(), exact);
        t9Exact = exact[0];
        t9Index = 0;
        String w = t9Words.isEmpty() ? fallback : t9Words.get(0);
        composing = caseT9(w);
        ic.setComposingText(composing, 1);
    }

    private List<String> t9Shown() {
        List<String> out = new ArrayList<>();
        // current pick first, then the rest
        for (int i = 0; i < t9Words.size() && out.size() < 3; i++) {
            String w = caseT9(t9Words.get((t9Index + i) % t9Words.size()));
            if (!out.contains(w)) out.add(w);
        }
        if (out.isEmpty() && !composing.isEmpty()) out.add(composing);
        return out;
    }
}
