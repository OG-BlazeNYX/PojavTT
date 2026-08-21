package com.pojavtiers.tagger.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pojavtiers.tagger.model.GameMode;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent client configuration for Pojav Tier Tagger.
 * Stored at {@code config/pojavtiertagger.json}.
 */
public class PojavConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("pojavtiertagger.json");

    public enum HighestMode {
        NEVER,
        IF_NONE,
        ALWAYS
    }

    // --- general ---
    public boolean enabled = true;
    public boolean showIcons = true;
    public boolean showInNametag = true;
    public boolean showInTab = true;
    public boolean showInChat = true;
    public boolean useBrackets = false;

    // --- behaviour: which gamemode's tier to show, and how to fall back when
    // that gamemode has no rank for a given player ---
    public GameMode gamemode = GameMode.SWORD;
    public HighestMode highestMode = HighestMode.IF_NONE;
    public int refreshIntervalMinutes = 30;

    public enum TierPosition {
        LEFT, RIGHT
    }

    // --- optional second tier badge, shown alongside the primary one ---
    public boolean secondaryTierEnabled = false;
    public GameMode secondaryGamemode = GameMode.MACE;
    // Which side of the player name the secondary badge renders on. The
    // primary badge always renders on the opposite side (LEFT stays adjacent
    // to the name as before when secondary is RIGHT, and vice versa).
    public TierPosition secondaryPosition = TierPosition.RIGHT;

    // --- separator between the tier badge and the name ---
    public String separator = " | ";

    // --- colours, keyed by tier code (HT1..LT5) as 0xRRGGBB ints ---
    public Map<String, Integer> tierColors = defaultColors();

    // Bumped each time the default tier palette changes, to force existing
    // configs (saved with an older default palette) onto the new one. Not
    // touched again after that so future user edits to tierColors are preserved.
    private int colorPaletteVersion = 0;
    private static final int CURRENT_PALETTE_VERSION = 2;

    // Colors copied 1:1 from the "mctiers" tier palette in the Tiers mod
    // (assets/minecraft/colors/mctiers.json - ht1..lt5 keys).
    public static Map<String, Integer> defaultColors() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("HT1", 0xE8BA3A);
        m.put("LT1", 0xD5B355);
        m.put("HT2", 0xC4D3E7);
        m.put("LT2", 0xA0A7B2);
        m.put("HT3", 0xF89F5A);
        m.put("LT3", 0xC67B42);
        m.put("HT4", 0x81749A);
        m.put("LT4", 0x655B79);
        m.put("HT5", 0x8F82A8);
        m.put("LT5", 0x655B79);
        return m;
    }

    public int getTierColor(String tierCode) {
        if (tierCode == null) return 0xD3D3D3;
        Integer c = tierColors.get(tierCode.toUpperCase());
        return c != null ? c : 0xD3D3D3;
    }

    // ------------------------------------------------------------------
    private static PojavConfig INSTANCE;

    public static PojavConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static PojavConfig load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    PojavConfig cfg = GSON.fromJson(reader, PojavConfig.class);
                    if (cfg != null) {
                        if (cfg.tierColors == null || cfg.tierColors.isEmpty()) {
                            cfg.tierColors = defaultColors();
                        }
                        if (cfg.colorPaletteVersion < CURRENT_PALETTE_VERSION) {
                            // Forces existing configs onto the current default palette.
                            cfg.tierColors = defaultColors();
                            cfg.colorPaletteVersion = CURRENT_PALETTE_VERSION;
                            cfg.save();
                        }
                        if (cfg.gamemode == null) cfg.gamemode = GameMode.SWORD;
                        if (cfg.highestMode == null) cfg.highestMode = HighestMode.IF_NONE;
                        if (cfg.secondaryGamemode == null) cfg.secondaryGamemode = GameMode.MACE;
                        if (cfg.secondaryPosition == null) cfg.secondaryPosition = TierPosition.RIGHT;
                        return cfg;
                    }
                }
            }
        } catch (Exception e) { }
        PojavConfig cfg = new PojavConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        INSTANCE = this;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) { }
    }
}
