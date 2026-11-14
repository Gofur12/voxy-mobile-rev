package dev.brezze.voxymobile.fabric;

import net.fabricmc.loader.api.FabricLoader;
import dev.brezze.voxymobile.core.config.LodDistancePolicy;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Independent far-LOD distance setting, separate from Minecraft's vanilla chunk distance. */
public final class LodDistanceConfig {
    public static final int DEFAULT_DISTANCE = LodDistancePolicy.DEFAULT_DISTANCE;
    public static final int MIN_DISTANCE = LodDistancePolicy.MIN_DISTANCE;
    public static final int MAX_DISTANCE = LodDistancePolicy.MAX_DISTANCE;
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
        String configured = properties.getProperty(KEY, Integer.toString(DEFAULT_DISTANCE));
        int distance = LodDistancePolicy.parse(configured);
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
