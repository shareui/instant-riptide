package de.shareui.instantriptide.mixin.client;

import de.shareui.instantriptide.InstantriptideDebugLog;
import de.shareui.instantriptide.InstantriptideLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public LocalPlayer player;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void instantriptide$deferPressToLegitSequence(final CallbackInfo ci) {
        if (this.player == null) { return; }
        if (InstantriptideLogic.isLegitSequenceRunning()) {
            InstantriptideDebugLog.log("startUseItem: swallowed, legit sequence already running");
            ci.cancel();
            return;
        }
        if (InstantriptideLogic.tryStartLegitSequence(this.player, InteractionHand.MAIN_HAND)) {
            InstantriptideDebugLog.log("startUseItem: intercepted, legit sequence started");
            ci.cancel();
        }
    }
}
