package com.pojavtiers.tagger.model;

import java.util.HashMap;
import java.util.Map;

/**
 * One player entry from the MMPvP rankings API.
 * <p>
 * The data comes from {@code GET https://mmpvp-production.up.railway.app/api/rankings},
 * which returns a top-level JSON array, e.g.:
 * <pre>
 * [
 *   {
 *     "discordId": "958640954636640296",
 *     "minecraftUsername": "Unknown",
 *     "region": "Unknown",
 *     "ranks": {
 *       "CART": "HT3",
 *       "SMP": "LT3",
 *       "DIA SMP": "LT3",
 *       "Sword": "LT3",
 *       "NethOP": "HT4",
 *       "Mace": "LT3",
 *       "Vanilla": "LT2",
 *       "UHC": "LT3",
 *       "Axe": "LT3",
 *       "Pot": "LT3"
 *     },
 *     "overallPoints": 76,
 *     "overallTier": "Novice"
 *   }
 * ]
 * </pre>
 * There is no single "best tier" field in the API response — {@code bestTier}
 * and {@code bestGameMode} below are computed client-side in
 * {@link com.pojavtiers.tagger.PojavTierManager} by scanning {@code ranks}
 * for the highest-scoring entry (see {@code tierPoints()}).
 */
public class PlayerRanking {

    public String discordId;
    public String minecraftUsername;      // from "minecraftUsername"
    public String region;                 // from "region"
    public Map<String, String> ranks = new HashMap<>(); // per-gamemode tiers, e.g. "Mace" -> "HT1"
    public int overallPoints;             // from "overallPoints"
    public String overallTier;            // from "overallTier"

    // ---------- Computed client-side (not present in the API response) ----------
    /** The best (highest-scoring) tier code across all recognized gamemodes in {@code ranks}. */
    public String bestTier;
    /** The gamemode that produced {@code bestTier}; null if none of the ranks map to a known GameMode. */
    public GameMode bestGameMode;

    /**
     * Returns the raw tier string (e.g. "HT3") for the given gamemode, or
     * {@code null} if the player has no entry for that mode in {@code ranks}.
     */
    public String getTier(GameMode mode) {
        if (ranks == null || mode == null) return null;
        // direct key first
        String value = ranks.get(mode.apiKey());
        if (value != null) return value;
        // case-insensitive fallback
        for (Map.Entry<String, String> entry : ranks.entrySet()) {
            if (GameMode.fromKey(entry.getKey()) == mode) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Returns {@code true} if the player has any per‑gamemode tier data. */
    public boolean hasAnyTier() {
        return ranks != null && !ranks.isEmpty();
    }
}
