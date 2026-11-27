package com.swipekey.app;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a finger path over the QWERTY keys into the most likely words.
 *
 * Each candidate word (picked by its first and last letters) is drawn as the line through its
 * key centres; that line and the finger path are both resampled to the same number of points
 * and compared. Common and often-picked words get a small head start.
 */
public final class SwipeDecoder {
    private static final int N = 40;

    public static List<String> decode(List<PointF> raw, PointF[] centres, float keyW, float keyH, Words words) {
        List<String> out = new ArrayList<>();
        if (raw.size() < 2 || !words.ready()) return out;

        // Work in "key units" so the maths is the same on every screen.
        float[][] user = new float[raw.size()][2];
        for (int i = 0; i < raw.size(); i++) {
            user[i][0] = raw.get(i).x / keyW;
            user[i][1] = raw.get(i).y / keyH;
        }
        float[][] cx = new float[26][];
        for (int i = 0; i < 26; i++)
            if (centres[i] != null) cx[i] = new float[]{centres[i].x / keyW, centres[i].y / keyH};

        float[][] u = resample(user, N);
        float userLen = length(user);
        String firsts = near(cx, user[0], 1.15f, 4);
        String lasts = near(cx, user[user.length - 1], 1.15f, 4);
        if (firsts.isEmpty() || lasts.isEmpty()) return out;

        List<Integer> cands = words.withEnds(firsts, lasts);
        int total = Math.max(1, words.list.size());
        double logTotal = Math.log(total + 1);

        final int keep = 5;
        String[] best = new String[keep];
        float[] bestScore = new float[keep];
        for (int i = 0; i < keep; i++) bestScore[i] = Float.MAX_VALUE;

        float[][] ideal = new float[32][];
        for (int idx : cands) {
            String plain = words.plain(idx);
            if (plain.length() < 2 || plain.length() > 30) continue;
            // the line through the word's keys (double letters are one stop)
            int n = 0;
            char prev = 0;
            boolean ok = true;
            for (int j = 0; j < plain.length() && n < ideal.length; j++) {
                char ch = plain.charAt(j);
                if (ch < 'a' || ch > 'z' || cx[ch - 'a'] == null) { ok = false; break; }
                if (ch == prev) continue;
                ideal[n++] = cx[ch - 'a'];
                prev = ch;
            }
            if (!ok || n < 1) continue;
            if (n == 1) { // "ll", "oo": a swipe can't make these well
                continue;
            }
            float[][] line = new float[n][];
            System.arraycopy(ideal, 0, line, 0, n);
            float idealLen = length(line);
            if (Math.abs(idealLen - userLen) > Math.max(2.2f, 0.55f * userLen)) continue;

            // every key in the word must be close to the finger's path
            float miss = 0;
            boolean far = false;
            for (int j = 0; j < n; j++) {
                float d = minDist(line[j], u);
                if (d > 1.05f) { far = true; break; }
                miss += d;
            }
            if (far) continue;
            miss /= n;

            float[][] l = resample(line, N);
            float shape = 0;
            for (int j = 0; j < N; j++) shape += dist(l[j], u[j]);
            shape /= N;

            float freq = (float) (0.3 * Math.log(1 + idx) / logTotal);
            int learned = Math.min(5, words.learnedCount(words.word(idx)));
            float score = shape + 0.6f * miss + freq - 0.06f * learned;

            // keep the five best
            for (int b = 0; b < keep; b++) {
                if (score < bestScore[b]) {
                    for (int m = keep - 1; m > b; m--) { bestScore[m] = bestScore[m - 1]; best[m] = best[m - 1]; }
                    bestScore[b] = score;
                    best[b] = words.word(idx);
                    break;
                }
            }
        }
        for (String s : best) if (s != null && !out.contains(s)) out.add(s);
        return out;
    }

    /** Letters whose key centre is within [radius] of p, nearest first. */
    private static String near(float[][] cx, float[] p, float radius, int max) {
        StringBuilder b = new StringBuilder();
        boolean[] used = new boolean[26];
        for (int k = 0; k < max; k++) {
            int bi = -1;
            float bd = Float.MAX_VALUE;
            for (int i = 0; i < 26; i++) {
                if (cx[i] == null || used[i]) continue;
                float d = dist(cx[i], p);
                if (d < bd) { bd = d; bi = i; }
            }
            if (bi < 0 || (k > 0 && bd > radius)) break;
            used[bi] = true;
            b.append((char) ('a' + bi));
        }
        return b.toString();
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float minDist(float[] p, float[][] path) {
        float m = Float.MAX_VALUE;
        for (float[] q : path) m = Math.min(m, dist(p, q));
        return m;
    }

    private static float length(float[][] pts) {
        float l = 0;
        for (int i = 1; i < pts.length; i++) l += dist(pts[i - 1], pts[i]);
        return l;
    }

    /** [n] evenly spaced points along the line. */
    static float[][] resample(float[][] pts, int n) {
        float[][] out = new float[n][2];
        float total = length(pts);
        if (pts.length == 1 || total == 0) {
            for (int i = 0; i < n; i++) { out[i][0] = pts[0][0]; out[i][1] = pts[0][1]; }
            return out;
        }
        float step = total / (n - 1);
        out[0][0] = pts[0][0]; out[0][1] = pts[0][1];
        int o = 1;
        float acc = 0;
        for (int i = 1; i < pts.length && o < n; i++) {
            float[] a = pts[i - 1], b = pts[i];
            float seg = dist(a, b);
            if (seg == 0) continue;
            float pos = 0;
            while (acc + (seg - pos) >= step && o < n) {
                float need = step - acc;
                pos += need;
                float t = pos / seg;
                out[o][0] = a[0] + (b[0] - a[0]) * t;
                out[o][1] = a[1] + (b[1] - a[1]) * t;
                o++;
                acc = 0;
            }
            acc += seg - pos;
        }
        while (o < n) { out[o][0] = pts[pts.length - 1][0]; out[o][1] = pts[pts.length - 1][1]; o++; }
        return out;
    }
}
