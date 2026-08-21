package com.pojavtiers.tagger.demo;

import com.pojavtiers.tagger.PojavTierManager;
import com.pojavtiers.tagger.model.GameMode;
import com.pojavtiers.tagger.model.PlayerRanking;

/**
 * A small, entirely offline "mannequin" player used by the config screen to
 * preview badge placement/colors/icons without a real player or a network
 * call. This mirrors the Tiers mod's bundled default-profile concept (its
 * {@code defaultProfile*.json} files + hardcoded UUID "ItzRealMe" demo
 * player) - here it's plain Java data instead of a loaded resource file, so
 * it can never fail to load (see CompatUtil's class comment for why we're
 * now avoiding fragile runtime resource/reflection lookups where a simple
 * direct approach works just as well).
 * <p>
 * Every {@link GameMode} has a tier here so switching the gamemode selector
 * in the config screen always has something to show.
 */
public final class DemoProfile {
    /** The mannequin's display name, shown under its preview skin in the config screen. */
    public static final String NAME = "__BlazeNYX";

    private static final PlayerRanking PROFILE = build();

    private DemoProfile() {}

    public static PlayerRanking get() {
        return PROFILE;
    }

    private static PlayerRanking build() {
        PlayerRanking pr = new PlayerRanking();
        pr.minecraftUsername = NAME;
        pr.region = "AS";
        pr.overallPoints = 310;
        pr.overallTier = "Expert";

        pr.ranks.put(GameMode.SWORD.apiKey(), "HT2");
        pr.ranks.put(GameMode.MACE.apiKey(), "LT3");
        pr.ranks.put(GameMode.SMP.apiKey(), "HT1");
        pr.ranks.put(GameMode.POT.apiKey(), "LT2");
        pr.ranks.put(GameMode.VANILLA.apiKey(), "HT4");
        pr.ranks.put(GameMode.NETHOP.apiKey(), "LT1");
        pr.ranks.put(GameMode.UHC.apiKey(), "HT3");
        pr.ranks.put(GameMode.AXE.apiKey(), "LT4");
        pr.ranks.put(GameMode.CART.apiKey(), "HT5");
        pr.ranks.put(GameMode.DIA_SMP.apiKey(), "LT5");

        PojavTierManager.computeBest(pr);
        return pr;
    }
}