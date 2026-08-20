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
        // Newer API (as of ~1.21.9): 4th constructor arg is a KeyBinding.Category, obtained via
        // KeyBinding.Category.create(Identifier).
        try {
            Class<?> categoryClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
            Method create = categoryClass.getMethod("create", Identifier.class);
            Object category = create.invoke(null, Identifier.of(modId, categoryPath));
            Constructor<?> ctor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class, categoryClass);
            return (KeyBinding) ctor.newInstance(translationKey, type, code, category);
        } catch (Throwable ignored) {
            // fall through
        }
        // Older API: 4th constructor arg is a plain String category translation key.
        try {
            Constructor<?> ctor = KeyBinding.class.getConstructor(String.class, InputUtil.Type.class, int.class, String.class);
            return (KeyBinding) ctor.newInstance(translationKey, type, code, legacyCategoryTranslationKey);
        } catch (Throwable ignored) {
            // fall through to a generic scan - the two hardcoded shapes above didn't match this build
        }

        // Generic fallback: scan every public constructor for one shaped
        // (String, <assignable from InputUtil.Type>, int, <anything>) and try to
        // satisfy the 4th argument regardless of its concrete type.
        for (Constructor<?> ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length != 4) continue;
            if (p[0] != String.class) continue;
            if (!InputUtil.Type.class.isAssignableFrom(p[1]) && p[1] != InputUtil.Type.class) continue;
            if (p[2] != int.class && p[2] != Integer.class) continue;

            Class<?> categoryParam = p[3];
            for (Object candidate : candidateCategoryValues(categoryParam, modId, categoryPath, legacyCategoryTranslationKey)) {
                try {
                    KeyBinding kb = (KeyBinding) ctor.newInstance(translationKey, type, code, candidate);
                    LOGGER.info("Registered keybinding '{}' via generic constructor scan (category param type: {})",
                            translationKey, categoryParam.getName());
                    return kb;
                } catch (Throwable ignored) {
                    // try next candidate
                }
            }
        }

        LOGGER.warn("Could not register keybinding '{}' on this Minecraft version - no matching KeyBinding "
                + "constructor found. Available constructors: {}", translationKey,
                java.util.Arrays.toString(KeyBinding.class.getConstructors()));
        return null;
    }

    /** Builds a list of plausible values to pass as the KeyBinding constructor's 4th (category) argument. */
    private static java.util.List<Object> candidateCategoryValues(Class<?> categoryParam, String modId,
                                                                    String categoryPath, String legacyKey) {
        java.util.List<Object> candidates = new java.util.ArrayList<>();
        if (categoryParam == String.class) {
            candidates.add(legacyKey);
            candidates.add(categoryPath);
            return candidates;
        }
        Identifier id = Identifier.of(modId, categoryPath);
        if (categoryParam == Identifier.class) {
            candidates.add(id);
            return candidates;
        }
        // Try a static factory method that takes an Identifier: create(Identifier), of(Identifier), etc.
        for (String factoryName : new String[]{"create", "of", "getOrCreate"}) {
            try {
                Method m = categoryParam.getMethod(factoryName, Identifier.class);
                candidates.add(m.invoke(null, id));
            } catch (Throwable ignored) {
                // try next
            }
        }
        // Try a public constructor of the category type that takes an Identifier.
        try {
            Constructor<?> c = categoryParam.getConstructor(Identifier.class);
            candidates.add(c.newInstance(id));
        } catch (Throwable ignored) {
            // try next
        }
        return candidates;
    }

    // ---------- CyclingButtonWidget.builder(Function) + .initially(T) (older) vs builder(Function, T-or-Supplier) (newer) ----------

    @SuppressWarnings("unchecked")
    public static <T> CyclingButtonWidget.Builder<T> cyclingBuilder(Function<T, Text> valueToText, T initial) {
        java.util.List<String> attemptLog = new java.util.ArrayList<>();

        // Try every "builder" overload whose first parameter is a Function, regardless of how many
        // parameters it has in total - don't guess the exact erased type of any parameter beyond the
        // first, just attempt plausible calls and let them fail over cleanly if the shape is wrong.
        for (Method m : CyclingButtonWidget.class.getMethods()) {
            if (!m.getName().equals("builder")) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 0 || !Function.class.isAssignableFrom(params[0])) continue;

            if (params.length == 2) {
                try {
                    return (CyclingButtonWidget.Builder<T>) m.invoke(null, valueToText, initial);
                } catch (Throwable t) {
                    attemptLog.add("builder(Function, T) with bare value: " + rootCause(t));
                }
                try {
                    java.util.function.Supplier<T> supplier = () -> initial;
                    return (CyclingButtonWidget.Builder<T>) m.invoke(null, valueToText, supplier);
                } catch (Throwable t) {
                    attemptLog.add("builder(Function, T) with Supplier: " + rootCause(t));
                }
            } else if (params.length == 1) {
                try {
                    Object builder = m.invoke(null, valueToText);
                    CyclingButtonWidget.Builder<T> result = tryInitially(builder, initial, attemptLog);
                    if (result != null) return result;
                } catch (Throwable t) {
                    attemptLog.add("builder(Function) then initially(): " + rootCause(t));
                }
            }
        }

        LOGGER.warn("Incompatible CyclingButtonWidget API on this Minecraft version - none of the {} "
                        + "attempted call shapes matched. Attempts: {}. Available 'builder' methods: {}",
                attemptLog.size(), attemptLog, availableBuilderMethods());
        throw new IllegalStateException("Incompatible CyclingButtonWidget API on this Minecraft version");
    }

    @SuppressWarnings("unchecked")
    private static <T> CyclingButtonWidget.Builder<T> tryInitially(Object builder, T initial, java.util.List<String> attemptLog) {
        for (Method initiallyM : builder.getClass().getMethods()) {
            if (!initiallyM.getName().equals("initially") || initiallyM.getParameterCount() != 1) continue;
            try {
                return (CyclingButtonWidget.Builder<T>) initiallyM.invoke(builder, initial);
            } catch (Throwable t) {
                attemptLog.add("initially(" + initiallyM.getParameterTypes()[0].getSimpleName() + "): " + rootCause(t));
            }
            // maybe it wants a Supplier<T> instead of a bare T
            try {
                java.util.function.Supplier<T> supplier = () -> initial;
                return (CyclingButtonWidget.Builder<T>) initiallyM.invoke(builder, supplier);
            } catch (Throwable ignored) {
                // continue scanning
            }
        }
        return null;
    }

    private static String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() != null ? ": " + c.getMessage() : "");
    }

    private static String availableBuilderMethods() {
        StringBuilder sb = new StringBuilder();
        for (Method m : CyclingButtonWidget.class.getMethods()) {
            if (!m.getName().equals("builder")) continue;
            sb.append(m).append("; ");
        }
        return sb.toString();
    }
}
