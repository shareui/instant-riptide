package de.shareui.instantriptide.mixin.client;

import de.shareui.instantriptide.InstantriptideDebugLog;
import de.shareui.instantriptide.client.LegitRiptideSequencer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityUseItemSyncMixin {
    @Inject(method = "updatingUsingItem", at = @At("HEAD"), cancellable = true)
    private void instantriptide$skipSyncDuringLegitSequence(final CallbackInfo ci) {
        if ((Object) this instanceof Player player && LegitRiptideSequencer.isSuppressingUseItemSync(player)) {
            InstantriptideDebugLog.log("updatingUsingItem sync skipped during legit sequence");
            ci.cancel();
        }
    }
}
