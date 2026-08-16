package com.pojavtiers.tagger.mixin;

import com.pojavtiers.tagger.PojavTierManager;
import com.pojavtiers.tagger.config.PojavConfig;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Injects tier badges before player names that appear in chat messages.
 * Targets the common funnel overload so both system and player messages pass
 * through.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private Text pojavtiertagger$decorate(Text message) {
        PojavConfig cfg = PojavConfig.get();
        if (!cfg.enabled || !cfg.showInChat) return message;
        Text replaced = PojavTierManager.deepReplace(message);
        return replaced != null ? replaced : message;
    }
}
