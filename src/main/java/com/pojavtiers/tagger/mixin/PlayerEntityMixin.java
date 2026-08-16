package com.pojavtiers.tagger.mixin;

import com.pojavtiers.tagger.PojavTierManager;
import com.pojavtiers.tagger.config.PojavConfig;
import com.pojavtiers.tagger.util.CompatUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends the Pojav tier badge to the floating nametag rendered above a
 * player's head (driven by {@link PlayerEntity#getDisplayName()}).
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void pojavtiertagger$appendTier(CallbackInfoReturnable<Text> cir) {
        PojavConfig cfg = PojavConfig.get();
        if (!cfg.enabled || !cfg.showInNametag) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        Text original = cir.getReturnValue();
        if (original == null) return;

        Text modified = PojavTierManager.appendTier(CompatUtil.profileName(self.getGameProfile()), original);
        if (modified != original) {
            cir.setReturnValue(modified);
        }
    }
}
