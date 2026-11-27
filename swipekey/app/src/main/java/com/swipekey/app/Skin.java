package com.swipekey.app;

/**
 * The AIM for Android phone and gadget skins, as keyboard colour sets.
 * Colours are copied from the AIM app's lib/ui/phone_theme.dart.
 */
public final class Skin {
    // What kind of gadget it is (same list as the AIM app).
    public static final int PHONE = 0, WIDEFLIP = 1, DASHBOARD = 2, POCKETMAIL = 3, MUSICPLAYER = 4,
            POCKETBRICK = 5, SWIVEL = 6, POPTILT = 7, COMPACT = 8, TACO = 9, LIPSTICK = 10, PAGER = 11,
            CORDLESS = 12, WATCH = 13, ROTATOR = 14, JELLY = 15, EGGPET = 16, WIDET9 = 17, PLAIN = 18;

    public static final int PATTERN_PLAIN = 0, PATTERN_DOTS = 1, PATTERN_LEOPARD = 2;

    /** Keyboard the skin uses. */
    public static final int KB_QWERTY = 0, KB_T9 = 1, KB_WIDE_T9 = 2, KB_CHOICE = 3;

    /** How the keyboard appears. */
    public static final int OPEN_FLIP = 0, OPEN_SLIDE = 1, OPEN_LAND = 2;

    public static final int SEND_GREEN = 0xFF3CCF6E;

    public final String id, name, blurb;
    public final int device;
    public final boolean flip;
    public final int ink, body, lid, shell, screenOn, screenInk, screenDim;
    public final int keyBg, keyInk, keySub, softBg, softInk, focus, keyInset;
    public final boolean glow;
    public final int pattern, shellPattern;
    /** Key corner radius in the AIM design canvas units (999 = pill). */
    public final float radius;
    public final boolean mono;

    Skin(String id, String name, String blurb, int device, boolean flip,
         int ink, int body, int lid, int shell, int screenOn, int screenInk, int screenDim,
         int keyBg, int keyInk, int keySub, int softBg, int softInk, int focus, int keyInset,
         boolean glow, int pattern, int shellPattern, float radius, boolean mono) {
        this.id = id; this.name = name; this.blurb = blurb; this.device = device; this.flip = flip;
        this.ink = ink; this.body = body; this.lid = lid; this.shell = shell;
        this.screenOn = screenOn; this.screenInk = screenInk; this.screenDim = screenDim;
        this.keyBg = keyBg; this.keyInk = keyInk; this.keySub = keySub;
        this.softBg = softBg; this.softInk = softInk; this.focus = focus; this.keyInset = keyInset;
        this.glow = glow; this.pattern = pattern; this.shellPattern = shellPattern;
        this.radius = radius; this.mono = mono;
    }

    /** Which keyboard this skin types with (same rules as the AIM app). */
    public int keyboard() {
        switch (device) {
            case PHONE: case WIDEFLIP: case PLAIN: return KB_CHOICE;
            case WIDET9: return KB_WIDE_T9;
            case SWIVEL: case COMPACT: case TACO: case CORDLESS: case WATCH: case ROTATOR: case JELLY:
                return KB_T9;
            default: return KB_QWERTY;
        }
    }

    /** True when the user's T9/QWERTY setting picks the keys. */
    public boolean switchable() { return keyboard() == KB_CHOICE; }

    public int opening(boolean qwerty) {
        if (flip || device == PAGER) return OPEN_FLIP;
        switch (device) {
            case PHONE: return OPEN_LAND;
            case WIDEFLIP: return qwerty ? OPEN_FLIP : OPEN_LAND;
            case DASHBOARD: case MUSICPLAYER: case POCKETBRICK: case SWIVEL: case POPTILT: case ROTATOR:
            case JELLY: case CORDLESS: case LIPSTICK:
                return OPEN_SLIDE;
            default: return OPEN_LAND;
        }
    }

    public String keysLabel() {
        switch (keyboard()) {
            case KB_CHOICE: return "Keypad or QWERTY";
            case KB_WIDE_T9: return "8×2 keypad";
            case KB_T9: return "Keypad (T9)";
            default: return "QWERTY + swipe";
        }
    }

    public static final Skin[] ALL = {
        new Skin("classic", "SwipeKey", "Plain dark keyboard", PLAIN, false, 0xFF0D0F14, 0xFF1E2129, 0xFF1E2129, 0xFF1E2129, 0xFF1E2129, 0xFFE8ECF2, 0xFF8A93A3, 0xFF3A3F4B, 0xFFFFFFFF, 0xFF9AA3B2, 0xFF2B2F38, 0xFFE8ECF2, 0xFF4FA3FF, 0, false, 0, 0, 10f, false),
        new Skin("brick", "Brick", "Chunky blue candybar", PHONE, false, 0xFF141A2B, 0xFF4A6FB0, 0xFF4A6FB0, 0xFF4A6FB0, 0xFFB5CC7A, 0xFF1F2A14, 0xFF46552F, 0xFFEEF1F6, 0xFF141A2B, 0xFF3F4B66, 0xFFFFD23F, 0xFF141A2B, 0xFFFF5A5F, 0, false, 0, 0, 20f, true),
        new Skin("ice", "Ice", "See-through purple", PHONE, false, 0xFF2A1866, 0xB89676F0, 0xB89676F0, 0xB89676F0, 0xFFF6F3FF, 0xFF2A1866, 0xFF6D5BB0, 0xB8FFFFFF, 0xFF2A1866, 0xFF4E3A9A, 0xFF7EE8FA, 0xFF2A1866, 0xFFFF7AC6, 0, false, 0, 0, 999f, false),
        new Skin("nightglow", "Night Glow", "Backlit orange keys", PHONE, false, 0xFF000000, 0xFF232329, 0xFF232329, 0xFF232329, 0xFF1F1200, 0xFFFFB347, 0xFFC98A3A, 0xFF2E2E36, 0xFFFFB347, 0xFFE0A65A, 0xFFFFB347, 0xFF000000, 0xFFFFFFFF, 0x73FFB347, true, 0, 0, 16f, true),
        new Skin("clamshell", "Clamshell", "Silver-blue flip", PHONE, true, 0xFF1C2A44, 0xFFC9D3E3, 0xFFC9D3E3, 0xFF9FB4D6, 0xFF071631, 0xFF8FD3FF, 0xFF4F86B8, 0xFFFFFFFF, 0xFF0E3A7A, 0xFF3D5F8F, 0xFF8FD3FF, 0xFF1C2A44, 0xFFFF5A5F, 0, false, 0, 0, 16f, false),
        new Skin("girlypop", "Girly Pop", "Pink polka-dot flip with a bow", PHONE, true, 0xFF7A1F52, 0xFFFF9CCF, 0xFFFFB8DC, 0xFFFF7FBF, 0xFFFFF0F7, 0xFFB0125E, 0xFFC95B94, 0xFFFFF6FB, 0xFFD6247A, 0xFF9C3F77, 0xFFC7A6FF, 0xFF7A1F52, 0xFF7B4AE2, 0, false, 1, 1, 26f, false),
        new Skin("gyaru", "Gyaru", "Gold leopard decoden flip", PHONE, true, 0xFF1A0A12, 0xFFE3A948, 0xFFFF2E93, 0xFFE3A948, 0xFF12030B, 0xFFFF7CC8, 0xFFC96AA1, 0xFF1A0A12, 0xFFFFD76A, 0xFFFF8FD0, 0xFFFFD76A, 0xFF1A0A12, 0xFFFFFFFF, 0xFFFF2E93, false, 2, 2, 18f, false),
        new Skin("wideflip", "Wide Flip", "Keypad phone that opens the long way to a keyboard", WIDEFLIP, false, 0xFF0E1116, 0xFF3B414B, 0xFF3B414B, 0xFF2A2E35, 0xFFE8F1FF, 0xFF0B1B33, 0xFF3A5478, 0xFF1C2027, 0xFFFFFFFF, 0xFF9FB3C8, 0xFF7FB6FF, 0xFF0E1116, 0xFFFFC233, 0, false, 0, 0, 12f, false),
        new Skin("dashboard", "Console TV", "Glowing green console keyboard under a tube TV", DASHBOARD, false, 0xFF041004, 0xFF2A2A2E, 0xFF2A2A2E, 0xFF2A2A2E, 0xFF031003, 0xFF8CFF5A, 0xFF4CB832, 0xFF3FA51F, 0xFFFFFFFF, 0xFFB8F58F, 0xFF8CFF5A, 0xFF041004, 0xFFD8FF7A, 0, true, 0, 0, 16f, true),
        new Skin("pocketmail", "Pocket Mail", "Thumb keyboard and a trackball", POCKETMAIL, false, 0xFF0B0C0F, 0xFF2D3038, 0xFF2D3038, 0xFF2D3038, 0xFFF2F6FA, 0xFF14202E, 0xFF5A6B80, 0xFF15161A, 0xFFFFFFFF, 0xFF8FA3B8, 0xFFB8BEC8, 0xFF0B0C0F, 0xFF5AB2FF, 0, false, 0, 0, 10f, false),
        new Skin("musicplayer", "Music Player", "Click-wheel player; a white keyboard slides up", MUSICPLAYER, false, 0xFF8E929A, 0xFFF7F7F5, 0xFFF7F7F5, 0xFFF7F7F5, 0xFFDDE7EF, 0xFF1B2733, 0xFF5B6F82, 0xFFFFFFFF, 0xFF3A3C42, 0xFF8E929A, 0xFFE6E7EA, 0xFF3A3C42, 0xFF3B82F6, 0, false, 0, 0, 16f, false),
        new Skin("pocketbrick", "Pocket Brick", "Chunky green-screen handheld", POCKETBRICK, false, 0xFF2B2A2E, 0xFFCBC7BD, 0xFFCBC7BD, 0xFFCBC7BD, 0xFF9BBC0F, 0xFF0F380F, 0xFF306230, 0xFFC4CFA1, 0xFF0F380F, 0xFF306230, 0xFF9A2257, 0xFFFFFFFF, 0xFF0F380F, 0, false, 0, 0, 16f, true),
        new Skin("swivel", "Swivel", "Orange music phone; the top spins round to show the keys", SWIVEL, false, 0xFF101418, 0xFFFF7A1A, 0xFFFF7A1A, 0xFFFF7A1A, 0xFFE9F6FF, 0xFF0E2233, 0xFF4E6A84, 0xFFF4F6F8, 0xFF101418, 0xFF5A6672, 0xFFFFD9B8, 0xFF101418, 0xFF0B6BCB, 0, false, 0, 0, 12f, false),
        new Skin("poptilt", "Pop-Tilt", "Messenger whose screen pops up over a thumb keyboard", POPTILT, false, 0xFF121418, 0xFF3FB8A8, 0xFF3FB8A8, 0xFF3FB8A8, 0xFFEAF7FF, 0xFF0F2437, 0xFF4B6A86, 0xFF2A2E35, 0xFFFFFFFF, 0xFF9FB3C8, 0xFF7CE0D2, 0xFF121418, 0xFFFFC233, 0, false, 0, 0, 10f, false),
        new Skin("compact", "Compact", "Round mirror compact; keys in a ring round a big 5", COMPACT, true, 0xFF5A1E3C, 0xFFE9C25A, 0xFFE8A3BC, 0xFFE9C25A, 0xFFFFF6FA, 0xFF8A2752, 0xFFC27A98, 0xFFFFF8E8, 0xFF8A2752, 0xFFB0657F, 0xFFF4D27A, 0xFF5A1E3C, 0xFF6B4BD6, 0, false, 0, 0, 999f, false),
        new Skin("taco", "Taco", "Sideways game phone: D-pad left, keys right", TACO, false, 0xFF0E1A12, 0xFF3B6B44, 0xFF3B6B44, 0xFF3B6B44, 0xFFC9E4A8, 0xFF163018, 0xFF3E6B42, 0xFFDDE7D6, 0xFF0E1A12, 0xFF46604F, 0xFFB8F04A, 0xFF0E1A12, 0xFFFFE066, 0, false, 0, 0, 12f, true),
        new Skin("lipstick", "Lipstick", "Mirror stick; spin the wheel to pick letters", LIPSTICK, false, 0xFF1A0710, 0xFFC21845, 0xFFC21845, 0xFFC21845, 0xFF2A0812, 0xFFFF3B6B, 0xFFC9506F, 0xFFF6DC8A, 0xFF1A0710, 0xFF7A5A14, 0xFFFFE1E9, 0xFF1A0710, 0xFFFFFFFF, 0, true, 0, 0, 14f, true),
        new Skin("pager", "Two-Way Pager", "Belt-clip pager; the lid opens over a tiny keyboard", PAGER, false, 0xFF0B0D10, 0xFF454C57, 0xFF454C57, 0xFF454C57, 0xFFB9C8A6, 0xFF16210F, 0xFF47573A, 0xFF2B2F36, 0xFFE6F0FF, 0xFF8FA3B8, 0xFF5AA9FF, 0xFF0B0D10, 0xFF5AA9FF, 0, false, 0, 0, 8f, true),
        new Skin("cordless", "Clear Cordless", "See-through house phone with neon guts", CORDLESS, false, 0xFF141B2E, 0xFF7FE8C8, 0xFF7FE8C8, 0xFF7FE8C8, 0xFFFFE39A, 0xFF3A2600, 0xFF8A6A20, 0xB3FFFFFF, 0xFF0C3B2E, 0xFF2F6F57, 0xFF7CFFB2, 0xFF141B2E, 0xFFFF4FA3, 0xFF7CFFB2, false, 0, 0, 14f, true),
        new Skin("watch", "Calculator Watch", "Databank watch; T9 on calculator keys", WATCH, false, 0xFF0E0F12, 0xFF9EA2AA, 0xFF9EA2AA, 0xFF9EA2AA, 0xFFB8C2A2, 0xFF1A1F12, 0xFF4D5640, 0xFF2A2C31, 0xFFF2F2F2, 0xFFF5A623, 0xFFF5A623, 0xFF0E0F12, 0xFFF5A623, 0, false, 0, 0, 8f, true),
        new Skin("rotator", "Rotator", "Candybar whose screen twists sideways", ROTATOR, false, 0xFF121621, 0xFF9C86F0, 0xFF9C86F0, 0xFF9C86F0, 0xFF0C1A33, 0xFF9AD8FF, 0xFF4D7FB0, 0xFFF7F9FC, 0xFF18315A, 0xFF5A6F8E, 0xFF9AD8FF, 0xFF121621, 0xFFFFD166, 0, false, 0, 0, 16f, false),
        new Skin("jelly", "Jelly Slider", "Squishy see-through gummy slider", JELLY, false, 0xFF0F5B4A, 0xFF5FE0B8, 0xFF5FE0B8, 0xFF5FE0B8, 0xFFF2FFF9, 0xFF0F5B4A, 0xFF4FA88E, 0xB3FFFFFF, 0xFF0F5B4A, 0xFF2E8A72, 0xFFFFE066, 0xFF0F5B4A, 0xFFE0407A, 0, false, 0, 0, 999f, false),
        new Skin("eggpet", "Egg Pet", "Keychain pet egg; three buttons pick letters", EGGPET, false, 0xFF3A1F5C, 0xFFC79BFF, 0xFFC79BFF, 0xFFC79BFF, 0xFFC8D8A8, 0xFF2A3A1A, 0xFF55663F, 0xFFF4E6FF, 0xFF3A1F5C, 0xFF7A5A9C, 0xFF7CE0FF, 0xFF3A1F5C, 0xFFFF7AB6, 0, false, 1, 0, 20f, true),
        new Skin("widet9", "Wide T9", "Two rows of eight keys for two thumbs", WIDET9, false, 0xFF1B1530, 0xFF5DB4F0, 0xFF5DB4F0, 0xFF5DB4F0, 0xFFDFF3FF, 0xFF10243A, 0xFF4A6A88, 0xFFFFF7E6, 0xFF1B1530, 0xFF6A5A8A, 0xFFFFC94D, 0xFF1B1530, 0xFFD6247A, 0, false, 0, 0, 16f, false),
    };

    public static Skin byId(String id) {
        for (Skin s : ALL) if (s.id.equals(id)) return s;
        return ALL[0];
    }
}
