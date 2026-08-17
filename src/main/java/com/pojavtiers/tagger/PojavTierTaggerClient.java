package com.pojavtiers.tagger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pojavtiers.tagger.config.PojavConfig;
import com.pojavtiers.tagger.gui.ConfigScreen;
import com.pojavtiers.tagger.model.GameMode;
import com.pojavtiers.tagger.model.PlayerRanking;
import com.pojavtiers.tagger.util.CompatUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class PojavTierTaggerClient implements ClientModInitializer {
    public static final String MOD_ID = "pojavtiertagger";
    private static KeyBinding cycleKey;

    @Override
    public void onInitializeClient() {
        PojavConfig.get(); // load config

        KeyBinding binding = CompatUtil.createKeyBinding(
                "pojavtiertagger.keybind.cycle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                MOD_ID,
                "pojavtiertagger",
                "key.categories.pojavtiertagger"
        );
        if (binding != null) {
            cycleKey = KeyBindingHelper.registerKeyBinding(binding);
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> PojavTierManager.refreshNow());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cycleKey != null && cycleKey.wasPressed()) {
                GameMode next = PojavConfig.get().gamemode.next();
                PojavConfig.get().gamemode = next;
                PojavConfig.get().save();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("[Pojav] Gamemode: ").formatted(Formatting.GRAY)
                                    .append(Text.literal(next.displayName()).formatted(Formatting.AQUA)),
                            true);
                }
            }
            if (client.world != null) {
                PojavTierManager.maybeRefresh();
            }
        });

        registerCommands();
    }

    private void registerCommands() {
        SuggestionProvider<FabricClientCommandSource> gamemodeSuggestions = (ctx, builder) -> {
            for (GameMode m : GameMode.values()) {
                if (m.apiKey().toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(m.apiKey());
                }
            }
            return builder.buildFuture();
        };

        SuggestionProvider<FabricClientCommandSource> playerSuggestions = (ctx, builder) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getPlayerList().forEach(e -> {
                    String name = CompatUtil.profileName(e.getProfile());
                    if (name != null && name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                        builder.suggest(name);
                    }
                });
            }
            return builder.buildFuture();
        };

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("pojavtier")
                        .executes(ctx -> {
                            PojavConfig cfg = PojavConfig.get();
                            cfg.enabled = !cfg.enabled;
                            cfg.save();
                            feedback(ctx.getSource(), Text.literal("[Pojav] Tags " +
                                    (cfg.enabled ? "enabled" : "disabled"))
                                    .formatted(cfg.enabled ? Formatting.GREEN : Formatting.RED));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("refresh").executes(ctx -> {
                            PojavTierManager.refreshNow(true);
                            feedback(ctx.getSource(), Text.literal("[Pojav] Refreshing rankings...")
                                    .formatted(Formatting.YELLOW));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("config").executes(ctx -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            mc.execute(() -> mc.setScreen(new ConfigScreen(null)));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("gamemode")
                                .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                        .suggests(gamemodeSuggestions)
                                        .executes(ctx -> {
                                            String arg = StringArgumentType.getString(ctx, "mode");
                                            GameMode mode = GameMode.fromKey(arg);
                                            if (mode == null) {
                                                feedback(ctx.getSource(), Text.literal("[Pojav] Unknown gamemode: " + arg)
                                                        .formatted(Formatting.RED));
                                                return 0;
                                            }
                                            PojavConfig.get().gamemode = mode;
                                            PojavConfig.get().save();
                                            feedback(ctx.getSource(), Text.literal("[Pojav] Gamemode set to " + mode.displayName())
                                                    .formatted(Formatting.GREEN));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("player")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .suggests(playerSuggestions)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            feedback(ctx.getSource(), printPlayerInfo(name));
                                            return 1;
                                        })))));
    }

    private static void feedback(FabricClientCommandSource source, Text text) {
        source.sendFeedback(text);
    }

    private static Text printPlayerInfo(String name) {
        PlayerRanking pr = PojavTierManager.lookup(name);
        if (pr == null) {
            return Text.literal("[Pojav] No data for " + name + " (try /pojavtier refresh)")
                    .formatted(Formatting.RED);
        }
        // FIX: Use MutableText so we can append
        MutableText text = Text.literal("=== Pojav tiers for " + pr.minecraftUsername + " ===")
                .formatted(Formatting.GOLD);
        if (pr.bestTier != null) {
            int color = PojavConfig.get().getTierColor(pr.bestTier);
            String modeName = pr.bestGameMode != null ? pr.bestGameMode.displayName() : "?";
            text.append(Text.literal("\nBest Tier: ").formatted(Formatting.GRAY))
                    .append(Text.literal(pr.bestTier + " (" + modeName + ")").styled(s -> s.withColor(color)));
        }
        text.append(Text.literal("\nPoints: ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(pr.overallPoints)).formatted(Formatting.WHITE));
        text.append(Text.literal("\nRegion: ").formatted(Formatting.GRAY))
                .append(Text.literal(pr.region == null ? "?" : pr.region).formatted(Formatting.WHITE));
        return text;
    }
}
