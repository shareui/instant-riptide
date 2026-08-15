package de.shareui.instantriptide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class InstantriptideConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(Instantriptide.MOD_ID + ".json");
    private static volatile InstantriptideConfig instance;

    // defaults
    public Mode mode = Mode.INSTANT;
    public boolean waterRefillEnabled = true;
    public boolean useElytraEnabled = false;

    public enum Mode {
        INSTANT,
        LEGIT
    }

    public static InstantriptideConfig get() {
        InstantriptideConfig loaded = instance;
        if (loaded == null) {
            synchronized (InstantriptideConfig.class) {
                loaded = instance;
                if (loaded == null) {
                    loaded = load();
                    instance = loaded;
                }
            }
        }
        return loaded;
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            Instantriptide.LOGGER.warn("failed to save config", e);
        }
    }

    private static InstantriptideConfig load() {
        if (!Files.isRegularFile(FILE)) {
            return new InstantriptideConfig();
        }
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            InstantriptideConfig loaded = GSON.fromJson(reader, InstantriptideConfig.class);
            return loaded != null ? loaded : new InstantriptideConfig();
        } catch (IOException e) {
            Instantriptide.LOGGER.warn("failed to load config, using defaults", e);
            return new InstantriptideConfig();
        }
    }
}
