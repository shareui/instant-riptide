package de.shareui.instantriptide.client;

import de.shareui.instantriptide.InstantriptideDebugLog;
import de.shareui.instantriptide.InstantriptideLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class LegitRiptideSequencer implements InstantriptideLogic.LegitRiptideHandler {
    private static final int AIM_DURATION_TICKS = 3;
    private static final int HOLD_DURATION_TICKS = 12;
    private static final int AWAIT_RELEASE_TIMEOUT_TICKS = 20;
    private static final float DOWNWARD_PITCH = 90.0f;
    private static volatile Player suppressedPlayer;
    private static volatile boolean suppressingSlotSwitch;

    private State state = State.IDLE;
    private int stateTicks;
    private Player player;
    private int tridentSlot;
    private float startPitch;
    private float startYaw;
    private float aimFromPitch;
    private float aimToPitch;
    private float aimFromYaw;
    private float aimToYaw;
    private boolean waterPoured;
    private BlockPos pouredWaterPos;
    private boolean releaseResolved;
    private boolean releaseLaunched;

    private enum State {
        IDLE,
        AIM_DOWN_TO_POUR,
        AIM_UP_AND_HOLD,
        AWAIT_RELEASE,
        AIM_UP_TO_RETURN
    }

    public static boolean isSuppressingUseItemSync(final Player checkedPlayer) {
        return suppressingSlotSwitch && suppressedPlayer == checkedPlayer;
    }

    @Override
    public boolean isRunning() {
        return state != State.IDLE;
    }

    @Override
    public void startSequence(final Player startPlayer, final InteractionHand usedHand) {
        this.player = startPlayer;
        this.tridentSlot = startPlayer.getInventory().getSelectedSlot();
        this.startPitch = startPlayer.getXRot();
        this.startYaw = startPlayer.getYRot();
        suppressedPlayer = startPlayer;
        suppressingSlotSwitch = false;
        InstantriptideDebugLog.log("startSequence hand={} tridentSlot={} startPitch={} {}",
                InstantriptideDebugLog.handName(usedHand), tridentSlot, startPitch, InstantriptideDebugLog.playerState(startPlayer));
        beginAim(startPitch, DOWNWARD_PITCH);
        beginYawAim(startYaw, startYaw);
        enterState(State.AIM_DOWN_TO_POUR);
    }

    public void tick() {
        if (state == State.IDLE) {
            return;
        }
        if (Minecraft.getInstance().player != player) {
            InstantriptideDebugLog.log("tick aborted: active client player changed, resetting sequence");
            reset();
            return;
        }
        stateTicks++;
        applyAimProgress();
        InstantriptideDebugLog.log("tick state={} stateTicks={} {}", state, stateTicks, InstantriptideDebugLog.playerState(player));
        switch (state) {
            case AIM_DOWN_TO_POUR -> tickAimDownToPour();
            case AIM_UP_AND_HOLD -> tickAimUpAndHold();
            case AWAIT_RELEASE -> tickAwaitRelease();
            case AIM_UP_TO_RETURN -> tickAimUpToReturn();
            default -> reset();
        }
    }

    private void tickAimDownToPour() {
        if (stateTicks < AIM_DURATION_TICKS) {
            return;
        }
        int waterSlot = InstantriptideLogic.findWaterBucketSlot(player);
        InstantriptideDebugLog.log("tickAimDownToPour: aim settled, switching to water bucket slot={}", waterSlot);
        player.stopUsingItem();
        suppressingSlotSwitch = true;
        switchHotbarSlot(waterSlot);
        InteractionResult result = Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
        waterPoured = result instanceof InteractionResult.Success;
        pouredWaterPos = waterPoured ? player.blockPosition() : null;
        InstantriptideDebugLog.log("tickAimDownToPour: useItem result={} waterPoured={} pos={}", result, waterPoured, pouredWaterPos);
        switchHotbarSlot(tridentSlot);
        suppressingSlotSwitch = false;
        beginAim(DOWNWARD_PITCH, startPitch);
        enterState(State.AIM_UP_AND_HOLD);
    }

    private void tickAimUpAndHold() {
        if (stateTicks == 1) {
            InstantriptideDebugLog.log("tickAimUpAndHold: starting trident charge via useItem");
            Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        if (stateTicks < Math.max(AIM_DURATION_TICKS, HOLD_DURATION_TICKS)) {
            return;
        }
        releaseResolved = false;
        releaseLaunched = false;
        InstantriptideLogic.awaitLegitRelease(player, launched -> {
            releaseResolved = true;
            releaseLaunched = launched;
            InstantriptideDebugLog.log("legit release callback resolved: launched={}", launched);
        });
        InstantriptideDebugLog.log("tickAimUpAndHold: hold duration reached, releasing trident");
        Minecraft.getInstance().gameMode.releaseUsingItem(player);
        enterState(State.AWAIT_RELEASE);
    }

    private void tickAwaitRelease() {
        if (!releaseResolved) {
            if (stateTicks >= AWAIT_RELEASE_TIMEOUT_TICKS) {
                InstantriptideDebugLog.log("tickAwaitRelease: timed out after {} ticks, giving up and returning camera", stateTicks);
                InstantriptideLogic.takeLegitReleaseListener(player);
                beginAim(player.getXRot(), startPitch);
                enterState(State.AIM_UP_TO_RETURN);
            }
            return;
        }
        boolean shouldRefill = InstantriptideLogic.shouldRefillWater(player);
        InstantriptideDebugLog.log("tickAwaitRelease: resolved launched={} waterPoured={} shouldRefill={}", releaseLaunched, waterPoured, shouldRefill);
        if (releaseLaunched && waterPoured) {
            ElytraAutoDeploy.Legit.arm(player);
        }
        if (releaseLaunched && waterPoured && shouldRefill) {
            pickUpWaterNow();
            return;
        }
        beginAim(player.getXRot(), startPitch);
        enterState(State.AIM_UP_TO_RETURN);
    }

    private void pickUpWaterNow() {
        if (pouredWaterPos == null || !player.isWithinBlockInteractionRange(pouredWaterPos, 0.0d)) {
            InstantriptideDebugLog.log("pickUpWaterNow: skipped, poured water out of reach pos={}", pouredWaterPos);
            beginAim(player.getXRot(), startPitch);
            beginYawAim(player.getYRot(), startYaw);
            enterState(State.AIM_UP_TO_RETURN);
            return;
        }
        snapAimAt(pouredWaterPos);
        player.stopUsingItem();
        int emptySlot = InstantriptideLogic.findEmptyBucketSlot(player);
        InstantriptideDebugLog.log("pickUpWaterNow: switching to empty bucket slot={}", emptySlot);
        suppressingSlotSwitch = true;
        switchHotbarSlot(emptySlot);
        Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
        switchHotbarSlot(tridentSlot);
        suppressingSlotSwitch = false;
        beginAim(DOWNWARD_PITCH, startPitch);
        beginYawAim(player.getYRot(), startYaw);
        enterState(State.AIM_UP_TO_RETURN);
    }

    private void snapAimAt(final BlockPos targetPos) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(targetPos);
        double dx = target.x() - eyePos.x();
        double dy = target.y() - eyePos.y();
        double dz = target.z() - eyePos.z();
        double horizontalDist = Math.sqrt((dx * dx) + (dz * dz));
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0d / Math.PI)) - 90.0f;
        float pitch = (float) (-(Mth.atan2(dy, horizontalDist) * (180.0d / Math.PI)));
        InstantriptideDebugLog.log("snapAimAt: target={} yaw={} pitch={}", targetPos, yaw, pitch);
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    private void tickAimUpToReturn() {
        if (stateTicks >= AIM_DURATION_TICKS) {
            InstantriptideDebugLog.log("tickAimUpToReturn: camera settled, sequence complete");
            reset();
        }
    }

    private void beginAim(final float fromPitch, final float toPitch) {
        this.aimFromPitch = fromPitch;
        this.aimToPitch = toPitch;
    }

    private void beginYawAim(final float fromYaw, final float toYaw) {
        this.aimFromYaw = fromYaw;
        this.aimToYaw = toYaw;
    }

    private void enterState(final State newState) {
        this.state = newState;
        this.stateTicks = 0;
    }

    private void applyAimProgress() {
        float linear = Math.min(1.0f, (float) stateTicks / AIM_DURATION_TICKS);
        float eased = linear * linear * (3.0f - 2.0f * linear);
        float newPitch = Mth.lerp(eased, aimFromPitch, aimToPitch);
        float newYaw = Mth.rotLerp(eased, aimFromYaw, aimToYaw);
        InstantriptideDebugLog.log("applyAimProgress: pitch {} -> {} yaw {} -> {} (eased={})", player.getXRot(), newPitch, player.getYRot(), newYaw, eased);
        player.setXRot(newPitch);
        player.setYRot(newYaw);
    }

    private void switchHotbarSlot(final int slot) {
        if (slot < 0) {
            InstantriptideDebugLog.log("switchHotbarSlot: skipped, slot=-1");
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() != slot) {
            InstantriptideDebugLog.log("switchHotbarSlot: {} -> {}", inventory.getSelectedSlot(), slot);
            inventory.setSelectedSlot(slot);
        }
    }

    private void reset() {
        InstantriptideDebugLog.log("reset: sequence returning to idle");
        this.state = State.IDLE;
        this.stateTicks = 0;
        this.player = null;
        this.waterPoured = false;
        this.pouredWaterPos = null;
        this.releaseResolved = false;
        this.releaseLaunched = false;
        suppressedPlayer = null;
        suppressingSlotSwitch = false;
    }
}
