package de.shareui.instantriptide.mixin;

import de.shareui.instantriptide.InstantriptideConfig;
import de.shareui.instantriptide.InstantriptideDebugLog;
import de.shareui.instantriptide.InstantriptideLogic;
import de.shareui.instantriptide.InstantriptideLogic.LegitReleaseListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TridentItem.class)
public class TridentItemMixin {

    // position the water was poured at by this mod during releaseUsing, read back at the method's return
    @Unique
    private BlockPos instantriptide$pouredWaterPos;

    // allows starting the riptide use on dry ground when eligible; warns once if no bucket is available
    // only relevant to instant mode: legit mode never reaches vanilla use() at all, since MinecraftMixin
    // intercepts the press before it and drives its own start/hold/release cycle instead.
    // also records the player's real wet state at this exact moment, so releaseUsing can still honor a
    // charge that legitimately started in water even if the player has since stepped onto dry ground
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
    private boolean instantriptide$forceWetOnUse(final Player playerInstance, final Level level, final Player player, final InteractionHand hand) {
        boolean actuallyWet = playerInstance.isInWaterOrRain();
        InstantriptideLogic.recordWetOnUseStart(player, actuallyWet);
        InstantriptideDebugLog.log("use forceWet: actuallyWet={} mode={} {}", actuallyWet, InstantriptideConfig.get().mode, InstantriptideDebugLog.playerState(player));
        if (actuallyWet) {
            return true;
        }
        if (InstantriptideConfig.get().mode != InstantriptideConfig.Mode.INSTANT) {
            InstantriptideDebugLog.log("use forceWet: rejected, mode is not instant");
            return false;
        }
        ItemStack tridentStack = player.getItemInHand(hand);
        if (!InstantriptideLogic.isEligibleForGroundRiptide(player, tridentStack)) {
            InstantriptideDebugLog.log("use forceWet: rejected, not eligible for ground riptide");
            return false;
        }
        if (!InstantriptideLogic.hasWaterBucketInHotbar(player)) {
            InstantriptideDebugLog.log("use forceWet: rejected, no water bucket in hotbar");
            InstantriptideLogic.notifyMissingWaterBucket(player);
            return false;
        }
        InstantriptideDebugLog.log("use forceWet: accepted, charge starting on dry ground");
        return true;
    }

    // pours the bucket and forces wetness for the actual riptide launch. also covers the reverse case:
    // the player was in water when the charge started but walked onto dry ground before releasing, which
    // vanilla would otherwise reject since it only checks wetness at release time
    @Redirect(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
    private boolean instantriptide$forceWetOnRelease(final Player playerInstance, final ItemStack stack, final Level level, final LivingEntity user, final int remainingUseTicks) {
        if (playerInstance.isInWaterOrRain()) {
            InstantriptideLogic.consumeWetOnUseStart(playerInstance);
            return true;
        }
        if (!(user instanceof Player player)) {
            return false;
        }
        if (InstantriptideLogic.consumeWetOnUseStart(player)) {
            InstantriptideDebugLog.log("releaseUsing forceWet: honoring wet-on-use-start, player now on dry ground");
            return true;
        }
        if (InstantriptideConfig.get().mode != InstantriptideConfig.Mode.INSTANT) {
            InstantriptideDebugLog.log("releaseUsing forceWet: rejected, mode is not instant");
            return false;
        }
        if (!InstantriptideLogic.isEligibleForGroundRiptide(player, stack)) {
            InstantriptideDebugLog.log("releaseUsing forceWet: rejected, not eligible for ground riptide {}", InstantriptideDebugLog.playerState(player));
            return false;
        }
        BlockPos pos = player.blockPosition();
        boolean poured = InstantriptideLogic.pourWaterBucketUnderPlayer(player, level, pos);
        instantriptide$pouredWaterPos = poured ? pos : null;
        InstantriptideDebugLog.log("releaseUsing forceWet: poured={} pos=({},{},{})", poured, pos.getX(), pos.getY(), pos.getZ());
        return poured;
    }

    // picks the poured water back into an empty bucket once the riptide launch actually fires (instant mode),
    // and reports the release outcome to the legit sequencer so it can pick its own poured water up in step
    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void instantriptide$onReleaseReturn(final ItemStack stack, final Level level, final LivingEntity user, final int remainingUseTicks, final CallbackInfoReturnable<Boolean> cir) {
        if (!(user instanceof Player player)) {
            return;
        }
        BlockPos pouredPos = instantriptide$pouredWaterPos;
        instantriptide$pouredWaterPos = null;
        boolean launched = cir.getReturnValueZ();
        InstantriptideDebugLog.log("releaseUsing returned: launched={} pouredPos={} clientSide={}", launched, pouredPos, level.isClientSide());
        if (pouredPos != null && launched) {
            InstantriptideLogic.pickUpWaterAt(player, level, pouredPos);
            // elytra auto-deploy is a purely client-side mechanism (it simulates a local key press), so
            // only notify from the client-side invocation of this inject; releaseUsing also runs on the
            // logical server for the integrated-server case, and letting that call through as well would
            // race the client's own arm() call for the same player and stomp its armed-player state with
            // an unrelated server-side Player object, aborting the deploy on the very next client tick
            if (level.isClientSide()) {
                InstantriptideDebugLog.log("releaseUsing returned: notifying instant ground-riptide listener (elytra arm path)");
                InstantriptideLogic.notifyInstantGroundRiptideLaunched(player);
            } else {
                InstantriptideDebugLog.log("releaseUsing returned: skipped elytra arm path, server side");
            }
        } else {
            InstantriptideDebugLog.log("releaseUsing returned: skipped elytra arm path, pouredPos={} launched={}", pouredPos, launched);
        }
        LegitReleaseListener listener = InstantriptideLogic.takeLegitReleaseListener(player);
        if (listener != null) {
            listener.onReleaseResolved(launched);
        }
    }
}
