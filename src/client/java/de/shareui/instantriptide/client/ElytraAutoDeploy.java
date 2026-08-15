package de.shareui.instantriptide.client;

import de.shareui.instantriptide.InstantriptideConfig;
import de.shareui.instantriptide.InstantriptideDebugLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public final class ElytraAutoDeploy {
    private ElytraAutoDeploy() {}

    public static final class Instant {
        private static volatile boolean armed;
        private static volatile Player armedPlayer;
        private static final SyntheticJumpRelease RELEASE_SCHEDULER = new SyntheticJumpRelease();

        private Instant() {}

        public static void arm(final Player player) {
            InstantriptideDebugLog.log("ElytraAutoDeploy.Instant.arm: called, useElytraEnabled={} {}",
                    InstantriptideConfig.get().useElytraEnabled, InstantriptideDebugLog.playerState(player));
            if (!InstantriptideConfig.get().useElytraEnabled) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Instant.arm: rejected, useElytraEnabled is off");
                return;
            }
            if (!hasElytraEquipped(player)) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Instant.arm: rejected, no elytra equipped");
                player.sendOverlayMessage(Component.translatable("message.instantriptide.no_elytra").withStyle(ChatFormatting.YELLOW));
                return;
            }
            InstantriptideDebugLog.log("ElytraAutoDeploy.Instant.arm: armed, waiting for player to leave ground");
            armedPlayer = player;
            armed = true;
        }

        public static void tick(final Minecraft client) {
            if (!armed) {
                return;
            }
            Player player = armedPlayer;
            if (player == null || client.player != player) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Instant.tick: aborted, active player changed");
                disarm();
                return;
            }
            if (player.onGround()) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Instant.tick: still armed, player still on ground");
                return;
            }
            InstantriptideDebugLog.log("ElytraAutoDeploy.Instant.tick: player left ground, deploying {}", InstantriptideDebugLog.playerState(player));
            pressJumpKeyForThisTick(client, player, RELEASE_SCHEDULER, "Instant");
            disarm();
        }

        private static void disarm() {
            armed = false;
            armedPlayer = null;
        }

        public static void tickReleaseScheduler() {
            RELEASE_SCHEDULER.tick();
        }
    }

    public static final class Legit {

        private static volatile boolean armed;
        private static volatile Player armedPlayer;
        private static final SyntheticJumpRelease RELEASE_SCHEDULER = new SyntheticJumpRelease();

        private Legit() {}

        public static void arm(final Player player) {
            InstantriptideDebugLog.log("ElytraAutoDeploy.Legit.arm: called, useElytraEnabled={} {}",
                    InstantriptideConfig.get().useElytraEnabled, InstantriptideDebugLog.playerState(player));
            if (!InstantriptideConfig.get().useElytraEnabled) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Legit.arm: rejected, useElytraEnabled is off");
                return;
            }
            if (!hasElytraEquipped(player)) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Legit.arm: rejected, no elytra equipped");
                player.sendOverlayMessage(Component.translatable("message.instantriptide.no_elytra").withStyle(ChatFormatting.YELLOW));
                return;
            }
            InstantriptideDebugLog.log("ElytraAutoDeploy.Legit.arm: armed, waiting for player to leave ground");
            armedPlayer = player;
            armed = true;
        }

        public static void tick(final Minecraft client) {
            if (!armed) {
                return;
            }
            Player player = armedPlayer;
            if (player == null || client.player != player) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Legit.tick: aborted, active player changed");
                disarm();
                return;
            }
            if (player.onGround()) {
                InstantriptideDebugLog.log("ElytraAutoDeploy.Legit.tick: still armed, player still on ground");
                return;
            }
            InstantriptideDebugLog.log("ElytraAutoDeploy.Legit.tick: player left ground, deploying {}", InstantriptideDebugLog.playerState(player));
            pressJumpKeyForThisTick(client, player, RELEASE_SCHEDULER, "Legit");
            disarm();
        }

        private static void disarm() {
            armed = false;
            armedPlayer = null;
        }

        public static void tickReleaseScheduler() {
            RELEASE_SCHEDULER.tick();
        }
    }

    private static void pressJumpKeyForThisTick(final Minecraft client, final Player player,
            final SyntheticJumpRelease releaseScheduler, final String modeLabel) {
        KeyMapping jumpKey = client.options.keyJump;
        if (jumpKey.isDown()) {
            InstantriptideDebugLog.log("ElytraAutoDeploy.{}.pressJumpKeyForThisTick: jump key already held, leaving to vanilla", modeLabel);
            return;
        }
        InstantriptideDebugLog.log("ElytraAutoDeploy.{}.pressJumpKeyForThisTick: simulating jump key press {}", modeLabel, InstantriptideDebugLog.playerState(player));
        jumpKey.setDown(true);
        releaseScheduler.scheduleRelease(jumpKey);
    }

    private static boolean hasElytraEquipped(final Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private static final class SyntheticJumpRelease {
        private volatile KeyMapping pendingRelease;
        void scheduleRelease(final KeyMapping jumpKey) {
            pendingRelease = jumpKey;
        }

        void tick() {
            KeyMapping jumpKey = pendingRelease;
            if (jumpKey == null) {
                return;
            }
            pendingRelease = null;
            jumpKey.setDown(false);
        }
    }
}
