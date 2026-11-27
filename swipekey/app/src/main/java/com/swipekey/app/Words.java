package com.swipekey.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The word list (most common first) plus the words the user keeps picking.
 * Loaded once on a background thread; everything is safe to call before it's ready
 * (you just get no suggestions yet).
 */
public final class Words {
    private static Words instance;

    /** All words, most common first. Words may contain an apostrophe ("don't"). */
    public final List<String> list = new ArrayList<>();
    /** Plain letters of each word ("dont"). */
    public final List<String> letters = new ArrayList<>();
    /** T9 key sequence -> word indexes, most common first. */
    private final Map<String, List<Integer>> byKeys = new HashMap<>();
    /** First + last letter -> word indexes (for swipe). */
    private final Map<String, List<Integer>> byEnds = new HashMap<>();
    private final Map<String, Integer> learned = new HashMap<>();
    private SharedPreferences prefs;
    private volatile boolean ready;

    private static final String T9 = "22233344455566677778889999";

    public static synchronized Words get(final Context ctx) {
        if (instance == null) {
            instance = new Words();
            final Context app = ctx.getApplicationContext();
            new Thread(new Runnable() {
                @Override public void run() { instance.load(app); }
            }, "SwipeKey words").start();
        }
        return instance;
    }

    public boolean ready() { return ready; }

    private void load(Context ctx) {
        try {
            prefs = ctx.getSharedPreferences("learned", Context.MODE_PRIVATE);
            BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open("words.txt"), "UTF-8"));
            String line;
            Set<String> seen = new HashSet<>();
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !seen.add(line)) continue;
                addWord(line);
            }
            r.close();
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                Object v = e.getValue();
                String w = e.getKey();
                if (!(v instanceof Integer)) continue;
                learned.put(w, (Integer) v);
                if (!seen.contains(w)) { seen.add(w); addWord(w); }
            }
        } catch (Exception ignored) {
        }
        ready = true;
    }

    private synchronized void addWord(String w) {
        String plain = w.replace("'", "");
        if (plain.isEmpty()) return;
        int i = list.size();
        list.add(w);
        letters.add(plain);
        String keys = keysFor(plain);
        if (keys == null) return;
        List<Integer> l = byKeys.get(keys);
        if (l == null) byKeys.put(keys, l = new ArrayList<>(2));
        l.add(i);
        if (plain.length() >= 2) {
            String ends = "" + plain.charAt(0) + plain.charAt(plain.length() - 1);
            List<Integer> e = byEnds.get(ends);
            if (e == null) byEnds.put(ends, e = new ArrayList<>());
            e.add(i);
        }
    }

    public static String keysFor(String word) {
        StringBuilder b = new StringBuilder(word.length());
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            if (c == '\'') continue;
            if (c < 'a' || c > 'z') return null;
            b.append(T9.charAt(c - 'a'));
        }
        return b.toString();
    }

    public int learnedCount(String w) {
        Integer n = learned.get(w);
        return n == null ? 0 : n;
    }

    /** The user picked or typed this word: rank it higher next time. */
    public synchronized void learn(String w) {
        if (!ready || w == null) return;
        w = w.toLowerCase();
        if (w.length() < 1 || w.length() > 24) return;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if ((c < 'a' || c > 'z') && c != '\'') return;
        }
        boolean known = byKeys.containsKey(keysFor(w)) && list.contains(w);
        int n = learnedCount(w) + 1;
        if (!known && n < 2) { // a new word has to be typed twice before it's added
            learned.put(w, n);
            prefs.edit().putInt(w, n).apply();
            return;
        }
        learned.put(w, n);
        if (!list.contains(w)) addWord(w);
        prefs.edit().putInt(w, n).apply();
    }

    /** T9: words for an exact key sequence, best first. */
    public synchronized List<String> t9(String keys, boolean[] exact) {
        List<String> out = new ArrayList<>();
        exact[0] = false;
        if (!ready) return out;
        List<Integer> ids = byKeys.get(keys);
        if (ids != null) {
            final List<Integer> sorted = new ArrayList<>(ids);
            Collections.sort(sorted, new Comparator<Integer>() {
                @Override public int compare(Integer a, Integer b) {
                    int la = learnedCount(list.get(a)), lb = learnedCount(list.get(b));
                    if (la != lb) return lb - la;
                    return a - b;
                }
            });
            for (int i : sorted) out.add(list.get(i));
            exact[0] = true;
            return out;
        }
        // No whole word yet: show the start of the most common longer word.
        int limit = Math.min(list.size(), 20000);
        for (int i = 0; i < limit && out.size() < 3; i++) {
            String p = letters.get(i);
            if (p.length() > keys.length()) {
                String k = keysFor(p.substring(0, keys.length()));
                if (keys.equals(k)) {
                    String stem = p.substring(0, keys.length());
                    if (!out.contains(stem)) out.add(stem);
                }
            }
        }
        return out;
    }

    /** Words that start with these letters, most common first. */
    public synchronized List<String> complete(String prefix, int max) {
        List<String> out = new ArrayList<>();
        if (!ready || prefix.isEmpty()) return out;
        String p = prefix.toLowerCase();
        int limit = Math.min(list.size(), 40000);
        String exact = null;
        for (int i = 0; i < limit && out.size() < max; i++) {
            String w = letters.get(i);
            if (w.startsWith(p)) {
                String word = list.get(i);
                if (w.equals(p)) { if (exact == null) exact = word; continue; }
                out.add(word);
            }
        }
        if (exact != null && !exact.equals(p)) out.add(0, exact); // "dont" -> "don't"
        return out;
    }

    /** Word indexes whose first and last letters are in these sets. */
    public synchronized List<Integer> withEnds(String firsts, String lasts) {
        List<Integer> out = new ArrayList<>();
        if (!ready) return out;
        for (int i = 0; i < firsts.length(); i++)
            for (int j = 0; j < lasts.length(); j++) {
                List<Integer> l = byEnds.get("" + firsts.charAt(i) + lasts.charAt(j));
                if (l != null) out.addAll(l);
            }
        return out;
    }

    public synchronized String word(int i) { return list.get(i); }
    public synchronized String plain(int i) { return letters.get(i); }
}
