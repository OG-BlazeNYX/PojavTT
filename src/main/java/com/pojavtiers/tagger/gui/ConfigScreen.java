package com.pojavtiers.tagger.gui;

import com.pojavtiers.tagger.PojavTierManager;
import com.pojavtiers.tagger.config.PojavConfig;
import com.pojavtiers.tagger.model.GameMode;
import com.pojavtiers.tagger.util.CompatUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A self-contained vanilla-widget config screen (no Cloth Config dependency).
 */
public class ConfigScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PojavTierTagger/ConfigScreen");

    private final Screen parent;
    private final PojavConfig cfg = PojavConfig.get();

    public ConfigScreen(Screen parent) {
        super(Text.literal("Pojav Tier Tagger Config"));   // changed title
        this.parent = parent;
    }

    @Override
    protected void init() {
        int colW = 150;
        int gap = 4;
        int rowH = 20;
        int leftX = this.width / 2 - colW - gap / 2;
        int rightX = this.width / 2 + gap / 2;
        int y = 40;

        // Every row below is wrapped in safeAdd() so that if ONE widget fails to
        // build on a given Minecraft version (e.g. a CyclingButtonWidget API
        // mismatch), it just skips that single button and logs why, instead of
        // throwing out of init() and leaving the whole screen with no buttons at all.

        // Row 1: enabled / icons
        safeAdd("enabled", () -> CyclingButtonWidget.onOffBuilder(cfg.enabled)
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.enabled"),
                        (b, v) -> cfg.enabled = v));
        safeAdd("showIcons", () -> CyclingButtonWidget.onOffBuilder(cfg.showIcons)
                .build(rightX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showIcons"),
                        (b, v) -> cfg.showIcons = v));
        y += rowH + gap;

        // Row 2: nametag / tab
        safeAdd("showInNametag", () -> CyclingButtonWidget.onOffBuilder(cfg.showInNametag)
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showInNametag"),
                        (b, v) -> cfg.showInNametag = v));
        safeAdd("showInTab", () -> CyclingButtonWidget.onOffBuilder(cfg.showInTab)
                .build(rightX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showInTab"),
                        (b, v) -> cfg.showInTab = v));
        y += rowH + gap;

        // Row 3: chat / brackets
        safeAdd("showInChat", () -> CyclingButtonWidget.onOffBuilder(cfg.showInChat)
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showInChat"),
                        (b, v) -> cfg.showInChat = v));
        safeAdd("useBrackets", () -> CyclingButtonWidget.onOffBuilder(cfg.useBrackets)
                .build(rightX, y, colW, rowH, Text.translatable("pojavtiertagger.config.bracket"),
                        (b, v) -> cfg.useBrackets = v));
        y += rowH + gap;

        // Row 4: gamemode (cycling)
        final int gamemodeY = y;
        safeAdd("gamemode", () -> CompatUtil.<GameMode>cyclingBuilder(g -> Text.literal(g.displayName()), cfg.gamemode)
                .values(GameMode.values())
                .build(leftX, gamemodeY, colW, rowH, Text.translatable("pojavtiertagger.config.gamemode"),
                        (b, v) -> cfg.gamemode = v));
        // highest mode
        safeAdd("highestMode", () -> CompatUtil.<PojavConfig.HighestMode>cyclingBuilder(m -> Text.literal(label(m)), cfg.highestMode)
                .values(PojavConfig.HighestMode.values())
                .build(rightX, gamemodeY, colW, rowH, Text.translatable("pojavtiertagger.config.highestMode"),
                        (b, v) -> cfg.highestMode = v));
        y += rowH + gap;

        // Row 5: refresh interval (cycling presets)
        Integer[] intervals = {5, 10, 15, 30, 60};
        Integer current = nearest(cfg.refreshIntervalMinutes, intervals);
        final int refreshY = y;
        safeAdd("refreshInterval", () -> CompatUtil.<Integer>cyclingBuilder(i -> Text.literal(i + " min"), current)
                .values(intervals)
                .build(leftX, refreshY, colW, rowH, Text.translatable("pojavtiertagger.config.refresh"),
                        (b, v) -> cfg.refreshIntervalMinutes = v));
        // manual refresh button (plain ButtonWidget - not affected by the cycling-button compat issue)
        safeAdd("refreshNowButton", () -> ButtonWidget.builder(
                        Text.literal("Refresh now (" + PojavTierManager.size() + " loaded)"),
                        b -> PojavTierManager.refreshNow(true))
                .dimensions(rightX, refreshY, colW, rowH).build());
        y += rowH + gap + 6;

        // Done
        final int doneY = y;
        safeAdd("done", () -> ButtonWidget.builder(Text.translatable("pojavtiertagger.config.done"), b -> close())
                .dimensions(this.width / 2 - 100, doneY, 200, rowH).build());
    }

    /** Builds and adds a widget, catching and logging any failure instead of aborting the whole screen init. */
    private void safeAdd(String rowName, java.util.function.Supplier<net.minecraft.client.gui.widget.ClickableWidget> factory) {
        try {
            addDrawableChild(factory.get());
        } catch (Throwable t) {
            LOGGER.warn("Could not build config screen widget '{}' on this Minecraft version - skipping it. "
                    + "Other buttons will still work.", rowName, t);
        }
    }

    private static String label(PojavConfig.HighestMode m) {
        return switch (m) {
            case NEVER -> "Highest: Never";
            case IF_NONE -> "Highest: If none";
            case ALWAYS -> "Highest: Always";
        };
    }

    private static Integer nearest(int value, Integer[] options) {
        Integer best = options[0];
        int diff = Integer.MAX_VALUE;
        for (Integer o : options) {
            int d = Math.abs(o - value);
            if (d < diff) {
                diff = d;
                best = o;
            }
        }
        return best;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFF);
    }

    @Override
    public void close() {
        cfg.save();
        PojavTierManager.clearCache();
        PojavTierManager.refreshNow();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
