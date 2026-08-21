package com.pojavtiers.tagger.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

/**
 * Small helpers for the handful of vanilla Minecraft APIs whose call shape is
 * easy to get wrong. These are all DIRECT compile-time calls (no reflection) -
 * every one of them was cross-checked against a second, independently-working
 * mod (Tiers) targeting the exact same Minecraft 1.21.11, to make sure the
 * signature used here is the real one and not a guess.
 * <p>
 * We previously used {@code Class.forName("net.minecraft...")} string-based
 * reflection here to guess at API shapes across versions. That was the actual
 * cause of the gamemode icons rendering as boxes and some config buttons never
 * appearing: Fabric Loom remaps class/method references inside normal
 * bytecode (plain {@code import} + direct calls), but does NOT remap class
 * names embedded in a plain string literal passed to {@code Class.forName}, so
 * in a real (production) launch those lookups quietly failed and every
 * feature that depended on them silently no-opped. Direct calls avoid that
 * failure mode entirely.
 */
public final class CompatUtil {

    private CompatUtil() {}

    /** GameProfile.name() - confirmed real on 1.21.11. */
    public static String profileName(GameProfile profile) {
        return profile != null ? profile.name() : null;
    }

    /**
     * Style.withFont(new StyleSpriteSource.Font(Identifier)) - confirmed real
     * on 1.21.11 (this is exactly how the icon font providers under
     * assets/pojavtiertagger/font/*.json get applied to text).
     */
    public static Style withFontCompat(Style style, Identifier fontId) {
        return style.withFont(new StyleSpriteSource.Font(fontId));
    }
}
