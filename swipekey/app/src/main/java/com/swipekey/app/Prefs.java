package com.swipekey.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Setting names, shared by the keyboard and the settings screen. */
public final class Prefs {
    public static final String SKIN = "skin";
    public static final String PHONES_QWERTY = "phones_qwerty";
    public static final String PLAIN_T9 = "plain_t9";
    public static final String SOUND = "sound";
    public static final String VIBRATE = "vibrate";
    public static final String SWIPE = "swipe";
    public static final String SUGGEST = "suggest";
    public static final String AUTOCAP = "autocap";
    public static final String PREDICTIVE = "predictive";
    public static final String ANIMATE = "animate";
    public static final String HEIGHT = "height";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences("swipekey", Context.MODE_PRIVATE);
    }
}
