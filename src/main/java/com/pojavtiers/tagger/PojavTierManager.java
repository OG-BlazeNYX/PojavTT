package com.pojavtiers.tagger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pojavtiers.tagger.config.PojavConfig;
import com.pojavtiers.tagger.model.GameMode;
import com.pojavtiers.tagger.model.PlayerRanking;
import com.pojavtiers.tagger.util.CompatUtil;
import com.pojavtiers.tagger.util.Http;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PojavTierManager {

    public static final Logger LOGGER = LoggerFactory.getLogger("PojavTierTagger");
    public static final Identifier ICON_FONT = Identifier.of("pojavtiertagger", "icons");

    private static final String API_URL = "https://mmpvp-production-69db.up.railway.app/api/rankings";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PojavTierTagger-fetch");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, PlayerRanking> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    public static volatile int cacheVersion = 0;
    public static volatile boolean loaded = false;
    private static volatile long lastAttemptMillis = 0L;
    private static volatile int failureStreak = 0;

    private PojavTierManager() {}

    public record DisplayedTier(GameMode mode, String tierCode) {}

    // ------------------------------------------------------------------
    // Networking
    // ------------------------------------------------------------------

    public static void maybeRefresh() {
        long now = System.currentTimeMillis();
        int intervalMin = Math.max(1, PojavConfig.get().refreshIntervalMinutes);
        long wait = loaded ? intervalMin * 60_000L : 60_000L;
        if (now - lastAttemptMillis >= wait) {
            refreshNow();
        }
    }

    public static void refreshNow() {
        refreshNow(false);
    }

    /** @param announce if true, always sends a chat result (success/failure) - used for manual refreshes. */
    public static void refreshNow(boolean announce) {
        if (!fetching.compareAndSet(false, true)) {
            if (announce) notifyPlayer("§e[Pojav] A refresh is already in progress...");
            return;
        }
        lastAttemptMillis = System.currentTimeMillis();

        EXEC.submit(() -> {
            try {
                String body = Http.get(API_URL);
                ingest(body, announce);
                failureStreak = 0;
            } catch (Exception e) {
                if (failureStreak == 0 || announce) {
                    LOGGER.warn("Failed to fetch Pojav rankings from {} : {}", API_URL, e.toString());
                    notifyPlayer("§c[Pojav] Could not load tiers: " + e);
                } else if (failureStreak % 20 == 0) {
                    LOGGER.warn("Still failing to fetch Pojav rankings ({} attempts): {}",
                            failureStreak, e.toString());
                }
                failureStreak++;
            } finally {
                fetching.set(false);
            }
        });
    }

    private static void notifyPlayer(String legacyText) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(legacyText), false);
            }
        });
    }

    private static void ingest(String body, boolean announce) {
        try {
            JsonArray playersArray = JsonParser.parseString(body).getAsJsonArray();

            Map<String, PlayerRanking> next = new ConcurrentHashMap<>();
            for (JsonElement elem : playersArray) {
                JsonObject obj = elem.getAsJsonObject();
                String ign = getJsonString(obj, "minecraftUsername");
                if (ign == null || ign.equalsIgnoreCase("unknown")) continue;

                PlayerRanking pr = new PlayerRanking();
                pr.minecraftUsername = ign;
                pr.discordId = getJsonString(obj, "discordId");
                pr.region = getJsonString(obj, "region");
                pr.overallPoints = getJsonInt(obj, "overallPoints");
                pr.overallTier = getJsonString(obj, "overallTier");

                JsonObject ranksObj = obj.getAsJsonObject("ranks");
                if (ranksObj != null) {
                    for (Map.Entry<String, JsonElement> rankEntry : ranksObj.entrySet()) {
                        JsonElement v = rankEntry.getValue();
                        if (v != null && !v.isJsonNull()) {
                            pr.ranks.put(rankEntry.getKey(), v.getAsString());
                        }
                    }
                }

                computeBest(pr);

                String key = ign.toLowerCase(Locale.ROOT);
                next.put(key, pr);
            }

            CACHE.clear();
            CACHE.putAll(next);
            boolean firstLoad = !loaded;
            loaded = true;
            cacheVersion++;
            LOGGER.info("Loaded {} Pojav player rankings", CACHE.size());
            if (firstLoad || announce) {
                notifyPlayer("§a[Pojav] Loaded " + CACHE.size() + " player tiers!");
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Pojav rankings", e);
            if (announce) {
                notifyPlayer("§c[Pojav] Failed to parse server response: " + e);
            }
        }
    }

    /**
     * Scans {@code pr.ranks} for the highest-scoring tier (per {@link #tierPoints})
     * among gamemodes that map to a known {@link GameMode}, and fills in
     * {@code pr.bestTier} / {@code pr.bestGameMode}. Unrecognized keys not covered
     * by {@link GameMode#fromKey} are skipped since there's no icon for them.
     */
    private static void computeBest(PlayerRanking pr) {
        if (pr.ranks == null || pr.ranks.isEmpty()) return;

        String bestTier = null;
        GameMode bestMode = null;
        int bestScore = -1;

        for (Map.Entry<String, String> entry : pr.ranks.entrySet()) {
            GameMode mode = GameMode.fromKey(entry.getKey());
            if (mode == null || mode == GameMode.OVERALL) continue; // no icon / not a real gamemode

            int score = tierPoints(entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestTier = entry.getValue();
                bestMode = mode;
            }
        }

        pr.bestTier = bestTier;
        pr.bestGameMode = bestMode;
    }

    private static String getJsonString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : null;
    }
    private static int getJsonInt(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsInt() : 0;
    }

    public static void clearCache() {
        CACHE.clear();
        loaded = false;
        cacheVersion++;
    }

    public static int size() {
        return CACHE.size();
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    public static PlayerRanking lookup(String username) {
        if (username == null) return null;
        return CACHE.get(username.toLowerCase(Locale.ROOT));
    }

    /** Returns the player's single best tier, with the icon of whichever gamemode earned it. */
    public static DisplayedTier resolve(String username) {
        PlayerRanking pr = lookup(username);
        if (pr == null || pr.bestTier == null) return null;

        GameMode mode = pr.bestGameMode != null ? pr.bestGameMode : GameMode.OVERALL;
        return new DisplayedTier(mode, pr.bestTier);
    }

    /** Points awarded for a tier code (PojavTiers scoring). */
    public static int tierPoints(String tier) {
        if (tier == null) return 0;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "HT1" -> 100;
            case "LT1" -> 90;
            case "HT2" -> 80;
            case "LT2" -> 70;
            case "HT3" -> 60;
            case "LT3" -> 50;
            case "HT4" -> 40;
            case "LT4" -> 30;
            case "HT5" -> 20;
            case "LT5" -> 10;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Text building
    // ------------------------------------------------------------------

    public static MutableText buildBadge(DisplayedTier dt) {
        PojavConfig cfg = PojavConfig.get();
        MutableText badge = Text.empty();

        if (cfg.showIcons) {
            // No external tint on the gamemode icon - render at neutral white (0xFFFFFF)
            // so the texture's own native colors show through unaltered.
            badge.append(Text.literal(String.valueOf(dt.mode().iconChar()))
                    .setStyle(CompatUtil.withFontCompat(Style.EMPTY, ICON_FONT).withColor(0xFFFFFF)));
            badge.append(Text.literal(" "));
        }

        String label = cfg.useBrackets ? "[" + dt.tierCode() + "]" : dt.tierCode();
        int color = cfg.getTierColor(dt.tierCode());
        badge.append(Text.literal(label).setStyle(Style.EMPTY.withColor(color)));
        return badge;
    }

    public static Text appendTier(String username, Text original) {
        DisplayedTier dt = resolve(username);
        if (dt == null) return original;

        MutableText result = buildBadge(dt);
        result.append(Text.literal(PojavConfig.get().separator).formatted(Formatting.GRAY));
        result.append(original);
        return result;
    }

    // ------------------------------------------------------------------
    // Chat deep replacement
    // ------------------------------------------------------------------

    private static Set<String> onlineNames() {
        Set<String> names = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return names;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String pname = CompatUtil.profileName(e.getProfile());
            if (pname != null) {
                names.add(pname);
            }
        }
        return names;
    }

    public static Text deepReplace(Text message) {
        if (message == null) return null;
        Set<String> names = onlineNames();
        if (names.isEmpty()) return message;
        return replaceNode(message, names);
    }

    private static Text replaceNode(Text node, Set<String> names) {
        MutableText self = node.copyContentOnly();
        self.setStyle(node.getStyle());

        String content = self.getString();
        String trimmed = content.trim();

        MutableText rebuilt;
        if (!trimmed.isEmpty() && names.contains(trimmed)) {
            DisplayedTier dt = resolve(trimmed);
            if (dt != null) {
                rebuilt = buildBadge(dt);
                rebuilt.append(Text.literal(PojavConfig.get().separator).formatted(Formatting.GRAY));
                rebuilt.append(self);
            } else {
                rebuilt = self;
            }
        } else {
            rebuilt = self;
        }

        for (Text sibling : node.getSiblings()) {
            rebuilt.append(replaceNode(sibling, names));
        }
        return rebuilt;
    }
}
