package dev.brezze.voxymobile.fabric;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Independent far-LOD distance setting, separate from Minecraft's vanilla chunk distance. */
public final class LodDistanceConfig {
    public static final int DEFAULT_DISTANCE = 2048;
    public static final int MIN_DISTANCE = 256;
    public static final int MAX_DISTANCE = 8192;
    private static final String KEY = "lodDistanceBlocks";

    private LodDistanceConfig() {}

    public static int load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("voxymobile.properties");
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException error) {
                System.err.println("[VoxyMobile] Could not read config: " + error.getMessage());
            }
        }
        int distance = DEFAULT_DISTANCE;
        try {
            distance = Integer.parseInt(properties.getProperty(KEY, Integer.toString(DEFAULT_DISTANCE)).trim());
        } catch (RuntimeException error) {
            System.err.println("[VoxyMobile] Invalid " + KEY + "; using " + DEFAULT_DISTANCE);
        }
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
        properties.setProperty(KEY, Integer.toString(distance));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                properties.store(writer, "VoxyMobile Reborn client settings");
            }
        } catch (IOException error) {
            System.err.println("[VoxyMobile] Could not save config: " + error.getMessage());
        }
        return distance;
    }
}
