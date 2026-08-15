package de.shareui.instantriptide;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public final class InstantriptideDebugLog {
    public static final boolean DEBUG = false;

    private InstantriptideDebugLog() {}

    public static boolean isActive() {
        return DEBUG;
    }

    public static void log(final String message) {
        if (!isActive()) { return; }
        Instantriptide.LOGGER.info("[instantriptide-debug] {}", message);
    }

    public static void log(final String format, final Object... args) {
        if (!isActive()) { return; }
        Instantriptide.LOGGER.info("[instantriptide-debug] " + format, args);
    }

    public static String playerState(final Player player) {
        BlockPos pos = player.blockPosition();
        return String.format(
                "pos=(%d,%d,%d) yaw=%.2f pitch=%.2f onGround=%b inWaterOrRain=%b",
                pos.getX(), pos.getY(), pos.getZ(),
                player.getYRot(), player.getXRot(),
                player.onGround(), player.isInWaterOrRain());
    }

    public static String handName(final InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? "main" : "off";
    }
}
