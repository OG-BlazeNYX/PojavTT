package com.pojavtiers.tagger.mixin;

import com.pojavtiers.tagger.PojavTierManager;
import com.pojavtiers.tagger.config.PojavConfig;
import com.pojavtiers.tagger.util.CompatUtil;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends the Pojav tier badge to a player's name in the tab list.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void pojavtiertagger$appendTier(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        PojavConfig cfg = PojavConfig.get();
        if (!cfg.enabled || !cfg.showInTab) return;
        if (entry == null || entry.getProfile() == null) return;

        Text original = cir.getReturnValue();
        if (original == null) return;

        Text modified = PojavTierManager.appendTier(CompatUtil.profileName(entry.getProfile()), original);
        if (modified != original) {
            cir.setReturnValue(modified);
        }
    }
}
