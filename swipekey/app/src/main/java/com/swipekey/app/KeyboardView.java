package com.swipekey.app;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws one skin's keyboard and turns touches into key presses and swipes.
 * Typing logic lives in {@link SwipeKeyService}; this view only reports what was touched.
 */
public class KeyboardView extends View {

    public interface Listener {
        void onKey(String code);
        void onLongKey(String code);
        void onSwipe(List<PointF> path);
        void onCandidate(int index);
        void onSkinPicked(String id);
        void onToggleLayout();
        void onPress();
    }

    public static final int L_QWERTY = 0, L_SYMBOLS = 1, L_T9 = 2, L_WIDE_T9 = 3;

    static final class Key {
        final String code, label;
        String sub;
        final float weight;
        final boolean gap, func;
        int bg, ink;
        final RectF r = new RectF();
        Key(String code, String label, String sub, float weight, boolean func) {
            this.code = code; this.label = label; this.sub = sub; this.weight = weight; this.func = func;
            this.gap = code == null;
        }
    }

    private Listener listener;
    private Skin skin = Skin.ALL[0];
    private int layout = L_QWERTY;
    private int shift; // 0 off, 1 once, 2 locked
    private String enterLabel = "⏎";
    private String t9Mode = "Abc";
    private boolean predictive = true;
    private boolean swipeOn = true;
    private float heightScale = 1f;
    private boolean picker;
    private String message;
    private final List<String> candidates = new ArrayList<>();
    private String hint = "";

    private final List<List<Key>> rows = new ArrayList<>();
    private final float dp;
    private final RectF strip = new RectF(), keysArea = new RectF();
    private final RectF skinBtn = new RectF(), layoutBtn = new RectF();
    private final List<RectF> candRects = new ArrayList<>();
    private final List<RectF> tileRects = new ArrayList<>();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bg;

    // touch
    private int activePointer = -1;
    private Key pressed;
    private float downX, downY;
    private boolean swiping, longFired, stripTouch;
    private final List<PointF> path = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (pressed == null || swiping) return;
            longFired = true;
            if ("DEL".equals(pressed.code)) {
                listener.onKey("DEL");
                handler.postDelayed(this, 60);
            } else {
                listener.onLongKey(pressed.code);
            }
            invalidate();
        }
    };

    // opening animation
    private long openStart;
    private boolean animate = true;
    private static final long OPEN_MS = 420;

    public KeyboardView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
        trail.setStrokeJoin(Paint.Join.ROUND);
        // Glowing skins use text shadows, which older Android only draws in software.
        if (android.os.Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null);
        build();
    }

    public void setListener(Listener l) { listener = l; }

    // ------------------------------------------------------------------ state from the service

    public void setSkin(Skin s) {
        if (s == skin) return;
        skin = s; bg = null; build(); invalidate();
    }
    public Skin getSkin() { return skin; }

    public void setLayout(int l) {
        if (l == layout) return;
        layout = l; build(); requestLayout(); invalidate();
    }
    public int getLayout() { return layout; }

    public void setShift(int s) { if (s != shift) { shift = s; invalidate(); } }
    public void setEnterLabel(String s) { enterLabel = s; build(); invalidate(); }
    public void setT9Mode(String mode, boolean predictive) {
        t9Mode = mode; this.predictive = predictive; build(); invalidate();
    }
    public void setSwipe(boolean on) { swipeOn = on; }
    public void setAnimate(boolean on) { animate = on; }
    public void setHeightScale(float s) { if (s != heightScale) { heightScale = s; requestLayout(); } }

    public void setCandidates(List<String> c, String hint) {
        candidates.clear();
        if (c != null) candidates.addAll(c);
        this.hint = hint == null ? "" : hint;
        message = null;
        invalidate();
    }

    public void flash(String msg) {
        message = msg;
        invalidate();
        handler.removeCallbacks(clearMsg);
        handler.postDelayed(clearMsg, 1800);
    }
    private final Runnable clearMsg = new Runnable() {
        @Override public void run() { message = null; invalidate(); }
    };

    public void setPicker(boolean on) { picker = on; invalidate(); }
    public boolean isPicker() { return picker; }

    /** Play the flip / slide / land animation (called each time the keyboard appears). */
    public void open() {
        openStart = animate ? SystemClock.uptimeMillis() : 0;
        picker = false;
        invalidate();
    }

    // ------------------------------------------------------------------ layouts

    private Key k(String code, String label) { return new Key(code, label, null, 1f, false); }
    private Key k(String code, String label, String sub) { return new Key(code, label, sub, 1f, false); }
    private Key f(String code, String label, float w) { return new Key(code, label, null, w, true); }
    private Key gap(float w) { return new Key(null, "", null, w, false); }

    private void build() {
        rows.clear();
        switch (layout) {
            case L_QWERTY: {
                rows.add(letters("qwertyuiop", "1234567890"));
                List<Key> r2 = letters("asdfghjkl", null);
                r2.add(0, gap(0.5f)); r2.add(gap(0.5f));
                rows.add(r2);
                List<Key> r3 = letters("zxcvbnm", null);
                r3.add(0, f("SHIFT", "⇧", 1.5f)); r3.add(f("DEL", "⌫", 1.5f));
                rows.add(r3);
                rows.add(bottom("SYM", "?123"));
                break;
            }
            case L_SYMBOLS: {
                rows.add(letters("1234567890", null));
                rows.add(letters("@#$%&-+()/", null));
                List<Key> r3 = letters("=*\"':;!?_", null);
                r3.add(f("DEL", "⌫", 1.5f));
                rows.add(r3);
                rows.add(bottom("ABC", "ABC"));
                break;
            }
            case L_T9: {
                rows.add(t9row("1", "2", "3"));
                rows.add(t9row("4", "5", "6"));
                rows.add(t9row("7", "8", "9"));
                rows.add(t9row("*", "0", "#"));
                List<Key> b = new ArrayList<>();
                b.add(f("SPACE", "space", 2f));
                b.add(f("DEL", "⌫", 1f));
                b.add(f("ENTER", enterLabel, 1f));
                rows.add(b);
                break;
            }
            case L_WIDE_T9: {
                // top: 1 2 3 | 7 8 9 ⌫ Send — bottom: 4 5 6 | * 0 # SPACE (same as the AIM app)
                List<Key> top = t9row("1", "2", "3");
                top.add(gap(0.5f));
                top.addAll(t9row("7", "8", "9"));
                top.add(f("DEL", "⌫", 1f));
                top.add(f("ENTER", enterLabel, 1f));
                List<Key> bot = t9row("4", "5", "6");
                bot.add(gap(0.5f));
                bot.addAll(t9row("*", "0", "#"));
                bot.add(f("SPACE", "space", 2f));
                rows.add(top); rows.add(bot);
                break;
            }
        }
        colourKeys();
        layoutKeys();
    }

    private List<Key> letters(String s, String subs) {
        List<Key> r = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            r.add(k(c, c, subs == null ? null : String.valueOf(subs.charAt(i))));
        }
        return r;
    }

    private List<Key> bottom(String symCode, String symLabel) {
        List<Key> r = new ArrayList<>();
        r.add(f(symCode, symLabel, 1.5f));
        r.add(k(",", ","));
        r.add(f("SPACE", skin.device == Skin.PLAIN ? "SwipeKey" : skin.name, 5f));
        r.add(k(".", "."));
        r.add(f("ENTER", enterLabel, 1.5f));
        return r;
    }

    private static final String[] T9_SUB = {"␣", ".,?!", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};

    private List<Key> t9row(String... codes) {
        List<Key> r = new ArrayList<>();
        for (String c : codes) {
            String sub;
            if (c.equals("*")) sub = "next";
            else if (c.equals("#")) sub = t9Mode + (predictive ? "" : " tap");
            else sub = T9_SUB[c.charAt(0) - '0'];
            if (c.equals("#")) r.add(new Key(c, "#", sub, 1f, false));
            else r.add(k(c, c, sub));
        }
        return r;
    }

    private void colourKeys() {
        for (List<Key> row : rows)
            for (Key key : row) {
                if ("ENTER".equals(key.code)) { key.bg = Skin.SEND_GREEN; key.ink = skin.device == Skin.PLAIN ? 0xFF0D1F12 : skin.ink; }
                else if (key.func) { key.bg = skin.softBg; key.ink = skin.softInk; }
                else { key.bg = skin.keyBg; key.ink = skin.keyInk; }
            }
    }

    private float rowHeight() {
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        float h = 50 * dp * heightScale * (land ? 0.74f : 1f);
        if (layout == L_T9) h *= 0.9f;
        if (layout == L_WIDE_T9) h *= 1.45f;
        return h;
    }

    private float stripHeight() { return 40 * dp; }
    private float pad() { return (skin.device == Skin.PLAIN ? 4 : 8) * dp; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (stripHeight() + pad() * 2 + rows.size() * rowHeight());
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg = null;
        layoutKeys();
    }

    private void layoutKeys() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        float p = pad();
        strip.set(p, p * 0.75f, w - p, stripHeight());
        keysArea.set(p, stripHeight() + p * 0.5f, w - p, h - p);
        float rowH = keysArea.height() / Math.max(1, rows.size());
        float maxWeight = 0;
        for (List<Key> row : rows) {
            float t = 0;
            for (Key key : row) t += key.weight;
            maxWeight = Math.max(maxWeight, t);
        }
        float unit = keysArea.width() / maxWeight;
        float y = keysArea.top;
        for (List<Key> row : rows) {
            float t = 0;
            for (Key key : row) t += key.weight;
            float x = keysArea.left + (keysArea.width() - t * unit) / 2;
            for (Key key : row) {
                key.r.set(x, y, x + key.weight * unit, y + rowH);
                x += key.weight * unit;
            }
            y += rowH;
        }
        // strip buttons
        float sh = strip.height();
        skinBtn.set(strip.left, strip.top, strip.left + sh * 1.25f, strip.bottom);
        if (skin.switchable() && (layout == L_QWERTY || layout == L_T9 || layout == L_SYMBOLS))
            layoutBtn.set(strip.right - sh * 1.4f, strip.top, strip.right, strip.bottom);
        else layoutBtn.setEmpty();
        // skin tiles
        tileRects.clear();
        int cols = w > h * 2.2f ? 8 : 4;
        int n = Skin.ALL.length;
        int tr = (n + cols - 1) / cols;
        float tw = keysArea.width() / cols, th = keysArea.height() / tr;
        for (int i = 0; i < n; i++) {
            float tx = keysArea.left + (i % cols) * tw, ty = keysArea.top + (i / cols) * th;
            tileRects.add(new RectF(tx + 2 * dp, ty + 2 * dp, tx + tw - 2 * dp, ty + th - 2 * dp));
        }
    }

    /** Centre of each letter key, for the swipe decoder (index = letter - 'a'). */
    public PointF[] letterCentres() {
        PointF[] out = new PointF[26];
        if (layout != L_QWERTY) return out;
        for (List<Key> row : rows)
            for (Key key : row)
                if (!key.func && !key.gap && key.code.length() == 1) {
                    char c = key.code.charAt(0);
                    if (c >= 'a' && c <= 'z') out[c - 'a'] = new PointF(key.r.centerX(), key.r.centerY());
                }
        return out;
    }

    public float keyWidth() { return rows.isEmpty() || rows.get(0).isEmpty() ? 1 : rows.get(0).get(0).r.width(); }
    public float keyHeight() { return rows.isEmpty() || rows.get(0).isEmpty() ? 1 : rows.get(0).get(0).r.height(); }

    // ------------------------------------------------------------------ drawing

    private float radiusPx(float h) {
        float r = skin.radius * 0.55f * dp;
        return Math.min(r, h / 2);
    }

    private boolean cartoon() { return skin.device != Skin.PLAIN; }

    private void ensureBg() {
        int w = getWidth(), h = getHeight();
        if (bg != null || w == 0 || h == 0) return;
        bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bg);
        paintBody(c, new RectF(0, 0, w, h), skin.body, skin.pattern, true);
    }

    /** Body fill, pattern and a thick ink outline round the top. */
    private void paintBody(Canvas c, RectF r, int colour, int pattern, boolean outline) {
        fill.setShader(null);
        fill.setColor(opaque(colour));
        float rad = cartoon() ? 22 * dp : 0;
        c.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(r.left, r.top, r.right, r.bottom + rad), rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        c.drawRect(r, fill);
        // a soft highlight band like the AIM cartoon shading
        if (cartoon()) {
            fill.setColor(0x22FFFFFF);
            c.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.35f, fill);
        }
        Random rnd = new Random(skin.id.hashCode());
        if (pattern == Skin.PATTERN_DOTS) {
            fill.setColor(0x66FFFFFF);
            float step = 22 * dp;
            for (float y = r.top + step / 2; y < r.bottom; y += step)
                for (float x = r.left + ((int) ((y - r.top) / step) % 2) * step / 2; x < r.right; x += step)
                    c.drawCircle(x, y, 4 * dp, fill);
        } else if (pattern == Skin.PATTERN_LEOPARD) {
            int spots = (int) (r.width() * r.height() / (900 * dp * dp));
            for (int i = 0; i < spots; i++) {
                float x = r.left + rnd.nextFloat() * r.width(), y = r.top + rnd.nextFloat() * r.height();
                float a = (5 + rnd.nextFloat() * 5) * dp, b = (4 + rnd.nextFloat() * 4) * dp;
                fill.setColor(0x55000000 | (skin.ink & 0xFFFFFF));
                c.drawOval(new RectF(x - a, y - b, x + a, y + b), fill);
                fill.setColor(opaque(colour));
                c.drawOval(new RectF(x - a * 0.5f, y - b * 0.45f, x + a * 0.5f, y + b * 0.45f), fill);
            }
        }
        c.restore();
        if (outline && cartoon()) {
            stroke.setColor(skin.ink);
            stroke.setStrokeWidth(3.5f * dp);
            RectF o = new RectF(r.left + 1.75f * dp, r.top + 1.75f * dp, r.right - 1.75f * dp, r.bottom + rad);
            c.drawRoundRect(o, rad, rad, stroke);
        }
    }

    private static int opaque(int c) {
        // See-through skins (Ice, Jelly, Cordless) sit on a dark backdrop in a keyboard window.
        int a = Color.alpha(c);
        if (a == 255) return c;
        float f = a / 255f;
        int base = 0xFF1A1A22;
        return Color.rgb((int) (Color.red(c) * f + Color.red(base) * (1 - f)),
                (int) (Color.green(c) * f + Color.green(base) * (1 - f)),
                (int) (Color.blue(c) * f + Color.blue(base) * (1 - f)));
    }

    @Override
    protected void onDraw(Canvas c) {
        ensureBg();
        if (bg != null) c.drawBitmap(bg, 0, 0, null);

        float t = openProgress();
        int open = skin.opening(layout == L_QWERTY || layout == L_SYMBOLS);

        drawStrip(c);

        c.save();
        c.clipRect(0, keysArea.top - pad() * 0.5f, getWidth(), getHeight());
        if (t < 1 && open == Skin.OPEN_SLIDE) c.translate(0, (1 - ease(t)) * (keysArea.height() + pad()));
        if (t < 1 && open == Skin.OPEN_LAND) {
            float s = 0.9f + 0.1f * overshoot(t);
            c.scale(1, s, getWidth() / 2f, getHeight());
        }
        if (picker) drawPicker(c);
        else for (List<Key> row : rows) for (Key key : row) if (!key.gap) drawKey(c, key);
        c.restore();

        if (t < 1 && open == Skin.OPEN_FLIP) drawLid(c, t);

        if (swiping && path.size() > 1) {
            trail.setColor((skin.focus & 0x00FFFFFF) | 0xCC000000);
            trail.setStrokeWidth(6 * dp);
            Path p = new Path();
            p.moveTo(path.get(0).x, path.get(0).y);
            for (int i = 1; i < path.size(); i++) p.lineTo(path.get(i).x, path.get(i).y);
            c.drawPath(p, trail);
        }
        if (t < 1) postInvalidateOnAnimation();
    }

    private float openProgress() {
        if (openStart == 0) return 1;
        float t = (SystemClock.uptimeMillis() - openStart) / (float) OPEN_MS;
        if (t >= 1) { openStart = 0; return 1; }
        return Math.max(0, t);
    }

    private static float ease(float t) { return 1 - (1 - t) * (1 - t) * (1 - t); }
    private static float overshoot(float t) {
        float s = 2.2f; t -= 1;
        return t * t * ((s + 1) * t + s) + 1;
    }

    /** Flip phones: the lid swings up and away to show the keys. */
    private void drawLid(Canvas c, float t) {
        float e = ease(t);
        float top = keysArea.top - pad() * 0.5f;
        float full = getHeight() - top;
        float h = full * (1 - e);
        if (h <= 1) return;
        RectF lid = new RectF(0, top, getWidth(), top + h);
        c.save();
        c.clipRect(lid);
        // lid outside, squashed as it turns away
        c.scale(1, Math.max(0.05f, 1 - e), getWidth() / 2f, top);
        RectF shellR = new RectF(0, top, getWidth(), top + full);
        fill.setColor(opaque(skin.shell));
        c.drawRect(shellR, fill);
        if (skin.shellPattern != Skin.PATTERN_PLAIN) {
            Skin keep = skin;
            paintBody(c, shellR, skin.shell, keep.shellPattern, false);
        }
        // little outer screen and hinge line
        fill.setColor(skin.ink);
        c.drawRect(0, top, getWidth(), top + 5 * dp, fill);
        RectF scr = new RectF(getWidth() / 2f - 40 * dp, top + full * 0.3f, getWidth() / 2f + 40 * dp, top + full * 0.3f + 34 * dp);
        c.drawRoundRect(scr, 8 * dp, 8 * dp, fill);
        fill.setColor(skin.screenOn);
        scr.inset(3 * dp, 3 * dp);
        c.drawRoundRect(scr, 6 * dp, 6 * dp, fill);
        text.setColor(skin.screenInk);
        text.setTextSize(13 * dp);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("AIM", scr.centerX(), scr.centerY() + 5 * dp, text);
        c.restore();
    }

    private void drawStrip(Canvas c) {
        boolean flat = !cartoon();
        // LCD screen look: the skin's screen colours.
        RectF s = new RectF(strip);
        float rad = flat ? 6 * dp : 10 * dp;
        if (!flat) {
            fill.setColor(skin.ink);
            c.drawRoundRect(s, rad, rad, fill);
            s.inset(2.5f * dp, 2.5f * dp);
        }
        fill.setColor(flat ? 0x00000000 : skin.screenOn);
        c.drawRoundRect(s, rad - 2 * dp, rad - 2 * dp, fill);

        Typeface face = skin.mono ? Typeface.MONOSPACE : Typeface.DEFAULT;
        int ink = skin.screenInk, dim = skin.screenDim;

        // skin button
        drawStripButton(c, skinBtn, picker ? "✕" : "◐", ink);
        if (!layoutBtn.isEmpty())
            drawStripButton(c, layoutBtn, (layout == L_T9) ? "ABC" : "T9", ink);

        float left = skinBtn.right + 4 * dp;
        float right = layoutBtn.isEmpty() ? s.right - 4 * dp : layoutBtn.left - 4 * dp;
        candRects.clear();
        text.setTypeface(face);
        text.setTextSize(16 * dp);
        float baseline = s.centerY() + 6 * dp;
        if (skin.glow) text.setShadowLayer(6 * dp, 0, 0, ink); else text.clearShadowLayer();

        String msg = picker ? "Pick a skin" : message;
        if (msg != null || candidates.isEmpty()) {
            text.setColor(msg != null ? ink : dim);
            text.setTypeface(Typeface.create(face, msg != null ? Typeface.BOLD : Typeface.NORMAL));
            String m = msg != null ? msg : hint;
            c.drawText(ellipsize(m, right - left), (left + right) / 2, baseline, text);
        } else {
            int n = Math.min(3, candidates.size());
            float w = (right - left) / n;
            for (int i = 0; i < n; i++) {
                RectF r = new RectF(left + i * w, s.top, left + (i + 1) * w, s.bottom);
                candRects.add(r);
                text.setColor(ink);
                text.setTypeface(Typeface.create(face, i == 0 ? Typeface.BOLD : Typeface.NORMAL));
                c.drawText(ellipsize(candidates.get(i), w - 6 * dp), r.centerX(), baseline, text);
                if (i > 0) {
                    fill.setColor(dim);
                    c.drawRect(r.left, r.top + r.height() * 0.25f, r.left + 1 * dp, r.bottom - r.height() * 0.25f, fill);
                }
            }
        }
        text.clearShadowLayer();
    }

    private void drawStripButton(Canvas c, RectF r, String label, int ink) {
        RectF b = new RectF(r);
        b.inset(5 * dp, 6 * dp);
        fill.setColor(skin.softBg);
        c.drawRoundRect(b, b.height() / 2, b.height() / 2, fill);
        if (cartoon()) {
            stroke.setColor(skin.ink);
            stroke.setStrokeWidth(2 * dp);
            c.drawRoundRect(b, b.height() / 2, b.height() / 2, stroke);
        }
        text.setColor(skin.softInk);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(13 * dp);
        c.drawText(label, b.centerX(), b.centerY() + 4.5f * dp, text);
    }

    private String ellipsize(String s, float max) {
        if (text.measureText(s) <= max) return s;
        while (s.length() > 1 && text.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void drawKey(Canvas c, Key key) {
        boolean down = key == pressed && !swiping;
        float g = (layout == L_WIDE_T9 ? 5 : 3) * dp;
        RectF r = new RectF(key.r.left + g, key.r.top + g, key.r.right - g, key.r.bottom - g);
        float shadow = cartoon() ? 3 * dp : 1.5f * dp;
        float rad = radiusPx(r.height());
        if (down) r.offset(0, shadow * 0.7f);

        // hard offset shadow
        if (!down) {
            fill.setColor(cartoon() ? skin.ink : 0x55000000);
            c.drawRoundRect(new RectF(r.left, r.top + shadow, r.right, r.bottom + shadow), rad, rad, fill);
        }
        fill.setColor(opaque(down ? blend(key.bg, skin.focus, 0.35f) : key.bg));
        c.drawRoundRect(r, rad, rad, fill);
        if (skin.keyInset != 0 && !key.func) {
            stroke.setColor(skin.keyInset);
            stroke.setStrokeWidth(1.5f * dp);
            RectF in = new RectF(r);
            in.inset(3 * dp, 3 * dp);
            c.drawRoundRect(in, Math.max(0, rad - 3 * dp), Math.max(0, rad - 3 * dp), stroke);
        }
        if (cartoon()) {
            stroke.setColor(down ? skin.focus : skin.ink);
            stroke.setStrokeWidth((down ? 3 : 2) * dp);
            c.drawRoundRect(r, rad, rad, stroke);
        }
        if ("SHIFT".equals(key.code) && shift > 0) {
            fill.setColor(skin.focus);
            c.drawCircle(r.right - 8 * dp, r.top + 8 * dp, (shift == 2 ? 4 : 3) * dp, fill);
        }

        // label
        String label = key.label;
        if (!key.func && layout == L_QWERTY && shift > 0) label = label.toUpperCase();
        if ("ENTER".equals(key.code)) label = enterLabel;
        boolean t9 = layout == L_T9 || layout == L_WIDE_T9;
        Typeface face = skin.mono ? Typeface.MONOSPACE : Typeface.DEFAULT;
        text.setTypeface(Typeface.create(face, Typeface.BOLD));
        if (skin.glow) text.setShadowLayer(5 * dp, 0, 0, key.ink); else text.clearShadowLayer();
        text.setColor(key.ink);
        float size;
        if (key.func) size = label.length() > 3 ? 14 * dp : 18 * dp;
        else if (t9) size = r.height() * 0.38f;
        else size = Math.min(r.height() * 0.45f, 22 * dp);
        text.setTextSize(size);
        String fitted = ellipsize(label, r.width() - 8 * dp);
        if (t9 && key.sub != null) {
            c.drawText(fitted, r.centerX(), r.centerY() + size * 0.1f, text);
            text.setTypeface(face);
            text.setTextSize(Math.max(10 * dp, r.height() * 0.2f));
            text.setColor(skin.keySub);
            c.drawText(key.sub, r.centerX(), r.centerY() + size * 0.1f + r.height() * 0.28f, text);
        } else {
            c.drawText(fitted, r.centerX(), r.centerY() + size * 0.36f, text);
            if (key.sub != null) {
                text.setTypeface(face);
                text.setTextSize(9 * dp);
                text.setColor(skin.keySub);
                c.drawText(key.sub, r.right - 7 * dp, r.top + 11 * dp, text);
            }
        }
        text.clearShadowLayer();
    }

    private void drawPicker(Canvas c) {
        Skin keep = skin;
        for (int i = 0; i < tileRects.size(); i++) {
            Skin s = Skin.ALL[i];
            RectF r = new RectF(tileRects.get(i));
            float rad = 10 * dp;
            fill.setColor(s.ink);
            c.drawRoundRect(new RectF(r.left, r.top + 2 * dp, r.right, r.bottom + 2 * dp), rad, rad, fill);
            fill.setColor(opaque(s.body));
            c.drawRoundRect(r, rad, rad, fill);
            stroke.setColor(s == keep ? keep.focus : s.ink);
            stroke.setStrokeWidth((s == keep ? 3.5f : 2) * dp);
            c.drawRoundRect(r, rad, rad, stroke);
            // three mini keys
            float kw = r.width() * 0.2f, kh = r.height() * 0.28f, ky = r.top + r.height() * 0.14f;
            for (int j = 0; j < 3; j++) {
                float kx = r.centerX() - kw * 1.6f + j * kw * 1.1f;
                RectF mk = new RectF(kx, ky, kx + kw, ky + kh);
                fill.setColor(opaque(j == 2 ? s.softBg : s.keyBg));
                c.drawRoundRect(mk, Math.min(kh / 2, s.radius * 0.3f * dp), Math.min(kh / 2, s.radius * 0.3f * dp), fill);
            }
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(Math.min(12 * dp, r.height() * 0.24f));
            text.setColor(readable(opaque(s.body)));
            c.drawText(ellipsize(s.name, r.width() - 6 * dp), r.centerX(), r.bottom - r.height() * 0.16f, text);
        }
    }

    private static int readable(int bg) {
        double l = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255;
        return l > 0.55 ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static int blend(int a, int b, float f) {
        return Color.argb(255,
                (int) (Color.red(a) * (1 - f) + Color.red(b) * f),
                (int) (Color.green(a) * (1 - f) + Color.green(b) * f),
                (int) (Color.blue(a) * (1 - f) + Color.blue(b) * f));
    }

    // ------------------------------------------------------------------ touch

    private Key keyAt(float x, float y) {
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Key> row : rows)
            for (Key key : row) {
                if (key.gap) continue;
                if (key.r.contains(x, y)) return key;
                float dx = Math.max(0, Math.max(key.r.left - x, x - key.r.right));
                float dy = Math.max(0, Math.max(key.r.top - y, y - key.r.bottom));
                float d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = key; }
            }
        return bestD < (20 * dp) * (20 * dp) ? best : null;
    }

    private boolean swipeable(Key k) {
        return swipeOn && layout == L_QWERTY && k != null && !k.func && k.code.length() == 1
                && Character.isLetter(k.code.charAt(0));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) return true;
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                // A second finger while the first is still down: finish the first as a tap.
                if (activePointer != -1) finishTouch(true);
                activePointer = e.getPointerId(idx);
                down(e.getX(idx), e.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int idx = e.findPointerIndex(activePointer);
                if (idx < 0 || pressed == null) return true;
                for (int h = 0; h < e.getHistorySize(); h++) move(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h));
                move(e.getX(idx), e.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                if (e.getPointerId(idx) != activePointer) return true;
                if (stripTouch) stripTap(e.getX(idx), e.getY(idx));
                else finishTouch(true);
                activePointer = -1;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                finishTouch(false);
                activePointer = -1;
                return true;
        }
        return true;
    }

    private void down(float x, float y) {
        handler.removeCallbacks(longPress);
        swiping = false; longFired = false; stripTouch = false;
        path.clear();
        downX = x; downY = y;
        if (y < keysArea.top - pad() * 0.25f) { stripTouch = true; pressed = null; return; }
        if (picker) {
            for (int i = 0; i < tileRects.size(); i++)
                if (tileRects.get(i).contains(x, y)) { listener.onPress(); listener.onSkinPicked(Skin.ALL[i].id); return; }
            return;
        }
        pressed = keyAt(x, y);
        if (pressed == null) return;
        listener.onPress();
        path.add(new PointF(x, y));
        handler.postDelayed(longPress, "DEL".equals(pressed.code) ? 380 : 420);
        invalidate();
    }

    private void move(float x, float y) {
        if (stripTouch || pressed == null) return;
        if (!swiping && swipeable(pressed)) {
            float d = (float) Math.hypot(x - downX, y - downY);
            if (d > keyWidth() * 0.75f) {
                swiping = true;
                handler.removeCallbacks(longPress);
            }
        }
        if (swiping || swipeable(pressed)) {
            PointF last = path.isEmpty() ? null : path.get(path.size() - 1);
            if (last == null || Math.hypot(x - last.x, y - last.y) > 2 * dp) path.add(new PointF(x, y));
        }
        if (swiping) invalidate();
    }

    private void finishTouch(boolean commit) {
        handler.removeCallbacks(longPress);
        Key k = pressed;
        boolean wasSwipe = swiping;
        pressed = null;
        swiping = false;
        if (commit && k != null) {
            if (wasSwipe && path.size() > 2) listener.onSwipe(new ArrayList<>(path));
            else if (!longFired) listener.onKey(k.code);
        }
        path.clear();
        invalidate();
    }

    private void stripTap(float x, float y) {
        stripTouch = false;
        if (Math.abs(y - downY) > strip.height() * 1.5f) return;
        if (skinBtn.contains(downX, strip.centerY())) {
            listener.onPress();
            picker = !picker;
            invalidate();
            return;
        }
        if (!layoutBtn.isEmpty() && layoutBtn.contains(downX, strip.centerY())) {
            listener.onPress();
            listener.onToggleLayout();
            return;
        }
        if (picker || message != null) return;
        for (int i = 0; i < candRects.size(); i++)
            if (candRects.get(i).contains(downX, strip.centerY())) {
                listener.onPress();
                listener.onCandidate(i);
                return;
            }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacksAndMessages(null);
    }
}
