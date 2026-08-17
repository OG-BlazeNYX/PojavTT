package com.pojavtiers.tagger.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Reflection shims for the handful of vanilla Minecraft APIs that changed shape
 * somewhere between 1.21 and 1.21.11 (this mod's supported range). This lets a
 * single compiled jar run on any version in that range: each method here tries
 * the newer API signature first, then falls back to the older one, resolved at
 * runtime rather than baked in at compile time.
 * <p>
 * Only add a method here once we've confirmed (via a real compile error) that
 * an API actually changed - don't reflection-wrap things defensively that we
 * haven't seen break.
 */
public final class CompatUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("PojavTierTagger/Compat");

    private CompatUtil() {}

    // ---------- GameProfile.getName() (<=1.21.10ish) vs GameProfile.name() (1.21.11+) ----------

    public static String profileName(GameProfile profile) {
        if (profile == null) return null;
        try {
            Method m = GameProfile.class.getMethod("name");
            return (String) m.invoke(profile);
        } catch (Throwable ignored) {
            // fall through to legacy accessor
        }
        try {
            Method m = GameProfile.class.getMethod("getName");
            return (String) m.invoke(profile);
        } catch (Throwable e) {
            LOGGER.warn("Could not read GameProfile name on this Minecraft version", e);
            return null;
        }
    }

    // ---------- Style.withFont(Identifier) (older) vs Style.withFont(StyleSpriteSource) (1.21.11+) ----------

    public static Style withFontCompat(Style style, Identifier fontId) {
        // Newer API: withFont(StyleSpriteSource), where StyleSpriteSource.Font(Identifier) is the plain-named-font case.
        try {
            Class<?> spriteSourceClass = Class.forName("net.minecraft.text.StyleSpriteSource");
            Class<?> fontRecordClass = Class.forName("net.minecraft.text.StyleSpriteSource$Font");
            Constructor<?> fontCtor = fontRecordClass.getConstructor(Identifier.class);
            Object fontSource = fontCtor.newInstance(fontId);
            Method withFont = Style.class.getMethod("withFont", spriteSourceClass);
            return (Style) withFont.invoke(style, fontSource);
        } catch (Throwable ignored) {
            // fall through to legacy signature
        }
        // Older API: withFont(Identifier) directly.
        try {
            Method withFont = Style.class.getMethod("withFont", Identifier.class);
            return (Style) withFont.invoke(style, fontId);
        } catch (Throwable e) {
            LOGGER.warn("Could not apply custom font style on this Minecraft version", e);
            return style;
        }
    }

    // ---------- KeyBinding(..., String category) (older) vs KeyBinding(..., KeyBinding.Category) (1.21.9+) ----------

    public static KeyBinding createKeyBinding(String translationKey, InputUtil.Type type, int code,
                                               String modId, String categoryPath, String legacyCategoryTranslationKey) {
        // Newer API: 4th constructor arg is a KeyBinding.Category, obtained via KeyBinding.Category.create(Identifier).
        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
            Method create = categoryClass.getMethod("create", Identifier.class);
            Object category = create.invoke(null, Identifier.of(modId, categoryPath));
            Constructor<?> ctor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class, categoryClass);
            return (KeyBinding) ctor.newInstance(translationKey, type, code, category);
        } catch (Throwable ignored) {
            // fall through to legacy signature
        }
        // Older API: 4th constructor arg is a plain String category translation key.
        try {
            Constructor<?> ctor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class, String.class);
            return (KeyBinding) ctor.newInstance(translationKey, type, code, legacyCategoryTranslationKey);
        } catch (Throwable e) {
            LOGGER.warn("Could not register keybinding on this Minecraft version", e);
            return null;
        }
    }

    // ---------- CyclingButtonWidget.builder(Function) + .initially(T) (older) vs builder(Function, T-or-Supplier) (newer) ----------

    @SuppressWarnings("unchecked")
    public static <T> CyclingButtonWidget.Builder<T> cyclingBuilder(Function<T, Text> valueToText, T initial) {
        // Try every 2-arg "builder" overload whose first parameter is a Function - don't guess the
        // exact erased type of the second parameter (it may be T-erased-to-Object, or a Supplier<T>,
        // depending on version), just attempt the call and let it fail over cleanly if the shape is wrong.
        for (Method m : CyclingButtonWidget.class.getMethods()) {
            if (!m.getName().equals("builder") || m.getParameterCount() != 2) continue;
            if (!Function.class.isAssignableFrom(m.getParameterTypes()[0])) continue;

            try {
                return (CyclingButtonWidget.Builder<T>) m.invoke(null, valueToText, initial);
            } catch (Throwable ignored) {
                // maybe the 2nd param wants a Supplier<T> instead of a bare T
            }
            try {
                java.util.function.Supplier<T> supplier = () -> initial;
                return (CyclingButtonWidget.Builder<T>) m.invoke(null, valueToText, supplier);
            } catch (Throwable ignored) {
                // try the next matching overload, if any
            }
        }

        // Older API: builder(Function) returns a Builder you then call .initially(T) on separately.
        for (Method m : CyclingButtonWidget.class.getMethods()) {
            if (!m.getName().equals("builder") || m.getParameterCount() != 1) continue;
            if (!Function.class.isAssignableFrom(m.getParameterTypes()[0])) continue;

            try {
                Object builder = m.invoke(null, valueToText);
                Method initiallyM = builder.getClass().getMethod("initially", Object.class);
                return (CyclingButtonWidget.Builder<T>) initiallyM.invoke(builder, initial);
            } catch (Throwable ignored) {
                // try the next matching overload, if any
            }
        }

        throw new IllegalStateException("Incompatible CyclingButtonWidget API on this Minecraft version");
    }
}
