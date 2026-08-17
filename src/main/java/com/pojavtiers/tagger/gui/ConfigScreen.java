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

/**
 * A self-contained vanilla-widget config screen (no Cloth Config dependency).
 */
public class ConfigScreen extends Screen {
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

        // Row 1: enabled / icons
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.enabled)
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.enabled"),
                        (b, v) -> cfg.enabled = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showIcons)
                .build(rightX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showIcons"),
                        (b, v) -> cfg.showIcons = v));
        y += rowH + gap;

        // Row 2: nametag / tab
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showInNametag)
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showInNametag"),
                        (b, v) -> cfg.showInNametag = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showInTab)
                .build(rightX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showInTab"),
                        (b, v) -> cfg.showInTab = v));
        y += rowH + gap;

        // Row 3: chat / brackets
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.showInChat)
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.showInChat"),
                        (b, v) -> cfg.showInChat = v));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(cfg.useBrackets)
                .build(rightX, y, colW, rowH, Text.translatable("pojavtiertagger.config.bracket"),
                        (b, v) -> cfg.useBrackets = v));
        y += rowH + gap;

        // Row 4: gamemode (cycling)
        addDrawableChild(CompatUtil.<GameMode>cyclingBuilder(g -> Text.literal(g.displayName()), cfg.gamemode)
                .values(GameMode.values())
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.gamemode"),
                        (b, v) -> cfg.gamemode = v));
        // highest mode
        addDrawableChild(CompatUtil.<PojavConfig.HighestMode>cyclingBuilder(m -> Text.literal(label(m)), cfg.highestMode)
                .values(PojavConfig.HighestMode.values())
                .build(rightX, y, colW, rowH, Text.translatable("pojavtiertagger.config.highestMode"),
                        (b, v) -> cfg.highestMode = v));
        y += rowH + gap;

        // Row 5: refresh interval (cycling presets)
        Integer[] intervals = {5, 10, 15, 30, 60};
        Integer current = nearest(cfg.refreshIntervalMinutes, intervals);
        addDrawableChild(CompatUtil.<Integer>cyclingBuilder(i -> Text.literal(i + " min"), current)
                .values(intervals)
                .build(leftX, y, colW, rowH, Text.translatable("pojavtiertagger.config.refresh"),
                        (b, v) -> cfg.refreshIntervalMinutes = v));
        // manual refresh button
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Refresh now (" + PojavTierManager.size() + " loaded)"),
                        b -> PojavTierManager.refreshNow(true))
                .dimensions(rightX, y, colW, rowH).build());
        y += rowH + gap + 6;

        // Done
        addDrawableChild(ButtonWidget.builder(Text.translatable("pojavtiertagger.config.done"), b -> close())
                .dimensions(this.width / 2 - 100, y, 200, rowH).build());
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
