package com.pojavtiers.tagger.model;

import java.util.Locale;

/**
 * The supported PvP gamemodes (plus a fallback “OVERALL” mode for when only a
 * best tier is known without a specific gamemode).
 * <p>
 * {@code apiKey} matches the keys used in the {@code ranks} object returned by
 * the old Pojav API and the new PojavTiers per‑player endpoint. The
 * {@code OVERALL} mode is not a real gamemode; it’s used internally to display
 * the player’s single best tier badge.
 * <p>
 * {@code iconChar} is a private‑use unicode codepoint mapped to a gamemode
 * emoji texture in {@code assets/pojavtiertagger/font/icons.json}.
 * {@code iconColor} is the default tint applied to that icon.
 */
public enum GameMode {
    SWORD("Sword", "Sword", '\uE000', 0xA4FDF0),
    MACE("Mace", "Mace", '\uE001', 0xAAAAAA),
    SMP("SMP", "SMP", '\uE002', 0xECCB45),
    POT("Pot", "Pot", '\uE003', 0xFF5555),
    VANILLA("Vanilla", "Vanilla", '\uE004', 0xFF55FF),
    NETHOP("NethOP", "Neth OP", '\uE005', 0x7D4A40),
    UHC("UHC", "UHC", '\uE006', 0xFF5555),
    AXE("Axe", "Axe", '\uE007', 0x55FF55),

    /** Generic fallback when only a best tier is known (no specific gamemode). */
    OVERALL("Overall", "Overall", '\uE008', 0xFFFFFF);

    private final String apiKey;
    private final String displayName;
    private final char iconChar;
    private final int iconColor;

    GameMode(String apiKey, String displayName, char iconChar, int iconColor) {
        this.apiKey = apiKey;
        this.displayName = displayName;
        this.iconChar = iconChar;
        this.iconColor = iconColor;
    }

    public String apiKey() {
        return apiKey;
    }

    public String displayName() {
        return displayName;
    }

    public char iconChar() {
        return iconChar;
    }

    public int iconColor() {
        return iconColor;
    }

    /** Cycles to the next gamemode in declaration order. */
    public GameMode next() {
        GameMode[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    /** Resolves a gamemode by its API key or enum name (case‑insensitive). */
    public static GameMode fromKey(String key) {
        if (key == null) return null;
        String k = key.trim();
        for (GameMode mode : values()) {
            if (mode.apiKey.equalsIgnoreCase(k) || mode.name().equalsIgnoreCase(k)) {
                return mode;
            }
        }
        // tolerate a few spelling variants seen in the wild
        String lower = k.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        for (GameMode mode : values()) {
            if (mode.apiKey.toLowerCase(Locale.ROOT).replace("_", "").equals(lower)) {
                return mode;
            }
        }
        if (lower.equals("nethpot") || lower.equals("netheritepot") || lower.equals("nethop")) return NETHOP;
        if (lower.equals("overall")) return OVERALL;   // <-- new fallback
        return null;
    }
}
