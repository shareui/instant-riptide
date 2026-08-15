package de.shareui.instantriptide;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

public class InstantriptideLogic {
    private static volatile boolean enabled = true;
    private static volatile LegitRiptideHandler legitHandler;
    private static volatile InstantGroundRiptideListener instantGroundRiptideListener;
    private static final Map<Player, LegitReleaseListener> LEGIT_RELEASE_LISTENERS = new WeakHashMap<>();
    private static final Map<Player, Boolean> WET_ON_USE_START = new WeakHashMap<>();

    private InstantriptideLogic() {}

    public interface LegitRiptideHandler {
        void startSequence(Player player, InteractionHand hand);
        boolean isRunning();
    }

    public interface LegitReleaseListener {
        void onReleaseResolved(boolean launched);
    }

    public interface InstantGroundRiptideListener {
        void onInstantGroundRiptideLaunched(Player player);
    }

    public static void registerLegitHandler(final LegitRiptideHandler handler) {
        legitHandler = handler;
    }
    public static void registerInstantGroundRiptideListener(final InstantGroundRiptideListener listener) {
        instantGroundRiptideListener = listener;
    }

    public static void notifyInstantGroundRiptideLaunched(final Player player) {
        InstantGroundRiptideListener listener = instantGroundRiptideListener;
        InstantriptideDebugLog.log("notifyInstantGroundRiptideLaunched: listenerRegistered={}", listener != null);
        if (listener != null) {
            listener.onInstantGroundRiptideLaunched(player);
        }
    }

    public static void awaitLegitRelease(final Player player, final LegitReleaseListener listener) {
        InstantriptideDebugLog.log("awaitLegitRelease registered");
        synchronized (LEGIT_RELEASE_LISTENERS) {
            LEGIT_RELEASE_LISTENERS.put(player, listener);
        }
    }

    public static LegitReleaseListener takeLegitReleaseListener(final Player player) {
        LegitReleaseListener listener;
        synchronized (LEGIT_RELEASE_LISTENERS) {
            listener = LEGIT_RELEASE_LISTENERS.remove(player);
        }
        InstantriptideDebugLog.log("takeLegitReleaseListener found={}", listener != null);
        return listener;
    }

    public static void recordWetOnUseStart(final Player player, final boolean wet) {
        synchronized (WET_ON_USE_START) {
            WET_ON_USE_START.put(player, wet);
        }
    }

    public static boolean consumeWetOnUseStart(final Player player) {
        Boolean wet;
        synchronized (WET_ON_USE_START) {
            wet = WET_ON_USE_START.remove(player);
        }
        return wet != null && wet;
    }

    public static boolean isLegitSequenceRunning() {
        LegitRiptideHandler handler = legitHandler;
        return handler != null && handler.isRunning();
    }

    public static boolean tryStartLegitSequence(final Player player, final InteractionHand hand) {
        InstantriptideDebugLog.log("tryStartLegitSequence hand={} {}", InstantriptideDebugLog.handName(hand), InstantriptideDebugLog.playerState(player));
        LegitRiptideHandler handler = legitHandler;
        if (handler == null || handler.isRunning()) {
            InstantriptideDebugLog.log("tryStartLegitSequence rejected: handler={} running={}", handler != null, handler != null && handler.isRunning());
            return false;
        }
        if (InstantriptideConfig.get().mode != InstantriptideConfig.Mode.LEGIT) {
            return false;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            InstantriptideDebugLog.log("tryStartLegitSequence rejected: not main hand");
            return false;
        }
        ItemStack heldStack = player.getItemInHand(hand);
        if (!isEligibleForGroundRiptide(player, heldStack)) {
            InstantriptideDebugLog.log("tryStartLegitSequence rejected: not eligible for ground riptide");
            return false;
        }
        if (!hasWaterBucketInHotbar(player)) {
            InstantriptideDebugLog.log("tryStartLegitSequence rejected: no water bucket in hotbar");
            notifyMissingWaterBucket(player);
            return false;
        }
        InstantriptideDebugLog.log("tryStartLegitSequence accepted, starting sequence");
        handler.startSequence(player, hand);
        return true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggleEnabled(final Player player) {
        enabled = !enabled;
        InstantriptideDebugLog.log("toggleEnabled: now enabled={}", enabled);
        Component message = enabled
                ? Component.translatable("message.instantriptide.enabled").withStyle(ChatFormatting.GREEN)
                : Component.translatable("message.instantriptide.disabled").withStyle(ChatFormatting.RED);
        player.sendOverlayMessage(message);
    }

    public static boolean isEligibleForGroundRiptide(final Player player, final ItemStack tridentStack) {
        if (!enabled) {
            InstantriptideDebugLog.log("isEligibleForGroundRiptide=false: mod disabled");
            return false;
        }
        if (player.isInWaterOrRain()) {
            InstantriptideDebugLog.log("isEligibleForGroundRiptide=false: already in water or rain");
            return false;
        }
        if (!player.onGround()) {
            InstantriptideDebugLog.log("isEligibleForGroundRiptide=false: not on ground");
            return false;
        }
        boolean hasRiptide = hasRiptide(tridentStack, player);
        InstantriptideDebugLog.log("isEligibleForGroundRiptide={}", hasRiptide);
        return hasRiptide;
    }

    public static boolean hasWaterBucketInHotbar(final Player player) {
        return findWaterBucketSlot(player.getInventory()) >= 0;
    }

    public static int findWaterBucketSlot(final Player player) {
        return findWaterBucketSlot(player.getInventory());
    }
    public static int findEmptyBucketSlot(final Player player) {
        return findEmptyBucketSlot(player.getInventory());
    }

    public static boolean pourWaterBucketUnderPlayer(final Player player, final Level level, final BlockPos pos) {
        InstantriptideDebugLog.log("pourWaterBucketUnderPlayer pos=({},{},{})", pos.getX(), pos.getY(), pos.getZ());
        Inventory inventory = player.getInventory();
        int slot = findWaterBucketSlot(inventory);
        if (slot < 0) {
            InstantriptideDebugLog.log("pourWaterBucketUnderPlayer failed: no water bucket slot");
            return false;
        }
        ItemStack bucketStack = inventory.getItem(slot);
        BucketItem bucketItem = (BucketItem) bucketStack.getItem();
        boolean placed = bucketItem.emptyContents(player, level, pos, null);
        if (!placed) {
            InstantriptideDebugLog.log("pourWaterBucketUnderPlayer failed: emptyContents returned false");
            return false;
        }
        bucketItem.checkExtraContent(player, level, bucketStack, pos);
        ItemStack emptiedStack = BucketItem.getEmptySuccessItem(bucketStack, player);
        inventory.setItem(slot, emptiedStack);
        InstantriptideDebugLog.log("pourWaterBucketUnderPlayer succeeded, slot={}", slot);
        return true;
    }

    public static void notifyMissingWaterBucket(final Player player) {
        player.sendOverlayMessage(Component.translatable("message.instantriptide.missing_bucket").withStyle(ChatFormatting.YELLOW));
    }

    public static boolean shouldRefillWater(final Player player) {
        return InstantriptideConfig.get().waterRefillEnabled && findEmptyBucketSlot(player) >= 0;
    }

    public static boolean pickUpWaterAt(final Player player, final Level level, final BlockPos pos) {
        InstantriptideDebugLog.log("pickUpWaterAt pos=({},{},{})", pos.getX(), pos.getY(), pos.getZ());
        if (!InstantriptideConfig.get().waterRefillEnabled) {
            InstantriptideDebugLog.log("pickUpWaterAt skipped: water refill disabled");
            return false;
        }
        Inventory inventory = player.getInventory();
        int slot = findEmptyBucketSlot(inventory);
        if (slot < 0) {
            InstantriptideDebugLog.log("pickUpWaterAt failed: no empty bucket slot");
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup bucketPickup)) {
            InstantriptideDebugLog.log("pickUpWaterAt failed: block at pos is not a BucketPickup");
            return false;
        }
        ItemStack filledStack = bucketPickup.pickupBlock(player, level, pos, state);
        if (filledStack.isEmpty()) {
            InstantriptideDebugLog.log("pickUpWaterAt failed: pickupBlock returned empty stack");
            return false;
        }
        bucketPickup.getPickupSound().ifPresent(sound -> player.playSound(sound, 1.0f, 1.0f));
        storeFilledBucket(inventory, slot, filledStack);
        InstantriptideDebugLog.log("pickUpWaterAt succeeded, slot={}", slot);
        return true;
    }

    private static void storeFilledBucket(final Inventory inventory, final int emptyBucketSlot, final ItemStack filledStack) {
        ItemStack emptyStack = inventory.getItem(emptyBucketSlot);
        emptyStack.shrink(1);
        if (emptyStack.isEmpty()) {
            inventory.setItem(emptyBucketSlot, filledStack);
            return;
        }
        if (!inventory.add(filledStack)) {
            inventory.player.drop(filledStack, false);
        }
    }

    private static boolean hasRiptide(final ItemStack tridentStack, final Player player) {
        Holder<Enchantment> riptideEntry = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.RIPTIDE);
        return EnchantmentHelper.getItemEnchantmentLevel(riptideEntry, tridentStack) > 0;
    }

    private static int findWaterBucketSlot(final Inventory inventory) {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isWaterBucket(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isWaterBucket(final ItemStack stack) {
        if (!(stack.getItem() instanceof BucketItem bucketItem)) {
            return false;
        }
        return bucketItem.getContent().is(FluidTags.WATER);
    }

    private static int findEmptyBucketSlot(final Inventory inventory) {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isEmptyBucket(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isEmptyBucket(final ItemStack stack) {
        return stack.getItem() == Items.BUCKET;
    }
}
