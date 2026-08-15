package de.shareui.instantriptide.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.shareui.instantriptide.Instantriptide;
import de.shareui.instantriptide.InstantriptideLogic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class InstantriptideClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Instantriptide.MOD_ID, "keys"));
    private static final LegitRiptideSequencer LEGIT_SEQUENCER = new LegitRiptideSequencer();

    private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.instantriptide.toggle",
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    CATEGORY));

    @Override
    public void onInitializeClient() {
        InstantriptideLogic.registerLegitHandler(LEGIT_SEQUENCER);
        InstantriptideLogic.registerInstantGroundRiptideListener(ElytraAutoDeploy.Instant::arm);
        ClientTickEvents.START_CLIENT_TICK.register(ElytraAutoDeploy.Instant::tick);
        ClientTickEvents.START_CLIENT_TICK.register(ElytraAutoDeploy.Legit::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_KEY.consumeClick()) {
                if (client.player != null) {
                    InstantriptideLogic.toggleEnabled(client.player); } }
            LEGIT_SEQUENCER.tick();
            ElytraAutoDeploy.Instant.tickReleaseScheduler();
            ElytraAutoDeploy.Legit.tickReleaseScheduler();
        });
    }
}
