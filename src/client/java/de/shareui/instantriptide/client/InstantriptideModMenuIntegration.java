package de.shareui.instantriptide.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.shareui.instantriptide.InstantriptideConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InstantriptideModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return InstantriptideModMenuIntegration::buildScreen;
    }

    private static Screen buildScreen(final Screen parent) {
        InstantriptideConfig config = InstantriptideConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.instantriptide.title"))
                .setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.instantriptide.category.general"));

        general.addEntry(entryBuilder.startEnumSelector(Component.translatable("config.instantriptide.mode"), InstantriptideConfig.Mode.class, config.mode)
                .setDefaultValue(InstantriptideConfig.Mode.INSTANT)
                .setEnumNameProvider(value -> modeName((InstantriptideConfig.Mode) value))
                .setTooltip(Component.translatable("config.instantriptide.mode.tooltip"))
                .setSaveConsumer(newValue -> config.mode = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.instantriptide.water_refill"), config.waterRefillEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.instantriptide.water_refill.tooltip"))
                .setSaveConsumer(newValue -> config.waterRefillEnabled = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("config.instantriptide.use_elytra"), config.useElytraEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.instantriptide.use_elytra.tooltip"))
                .setSaveConsumer(newValue -> config.useElytraEnabled = newValue)
                .build());

        return builder.build();
    }

    private static Component modeName(final InstantriptideConfig.Mode mode) {
        return switch (mode) {
            case INSTANT -> Component.translatable("config.instantriptide.mode.instant");
            case LEGIT -> Component.translatable("config.instantriptide.mode.legit");
        };
    }
}
