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
    private static final String THREADS_KEY = "serviceThreads";
    private static final String ENABLED_KEY = "enabled";
    private static final String RENDERER_KEY = "rendererEnabled";
    private static final String INGEST_KEY = "terrainIngestionEnabled";
    private static final String SEAM_DEBUG_KEY = "seamDebugOverlayEnabled";
    private static int currentDistance = DEFAULT_DISTANCE;
    private static int serviceThreads = 1;
    private static boolean enabled = true;
    private static boolean rendererEnabled = true;
    private static boolean terrainIngestionEnabled = true;
    private static boolean seamDebugOverlayEnabled = false;

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
        currentDistance = distance;
        serviceThreads = parseThreads(properties.getProperty(THREADS_KEY));
        enabled = Boolean.parseBoolean(properties.getProperty(ENABLED_KEY, "true"));
        rendererEnabled = Boolean.parseBoolean(properties.getProperty(RENDERER_KEY, "true"));
        terrainIngestionEnabled = Boolean.parseBoolean(properties.getProperty(INGEST_KEY, "true"));
        seamDebugOverlayEnabled = Boolean.parseBoolean(properties.getProperty(SEAM_DEBUG_KEY, "false"));
        properties.setProperty(KEY, Integer.toString(distance));
        properties.setProperty(ENABLED_KEY, Boolean.toString(enabled));
        properties.setProperty(THREADS_KEY, Integer.toString(serviceThreads));
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

    public static int distance() { return currentDistance; }
    public static int serviceThreads() { return serviceThreads; }
    public static void setServiceThreads(int value) { serviceThreads = Math.max(1, Math.min(4, value)); save(); }
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; save(); }
    public static boolean rendererEnabled() { return rendererEnabled; }
    public static boolean terrainIngestionEnabled() { return terrainIngestionEnabled; }
    public static boolean seamDebugOverlayEnabled() { return seamDebugOverlayEnabled; }

    public static void setDistance(int distance) { currentDistance = LodDistancePolicy.normalize(distance); save(); }
    public static void setRendererEnabled(boolean enabled) { rendererEnabled = enabled; save(); }
    public static void setTerrainIngestionEnabled(boolean enabled) { terrainIngestionEnabled = enabled; save(); }
    public static void setSeamDebugOverlayEnabled(boolean enabled) { seamDebugOverlayEnabled = enabled; save(); }

    private static int parseThreads(String value) {
        try { return Math.max(1, Math.min(4, Integer.parseInt(value))); }
        catch (RuntimeException ignored) { return 1; }
    }
    private static void save() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("voxymobile.properties");
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) { properties.load(reader); }
            catch (IOException error) { System.err.println("[VoxyMobile] Could not reload config before save: " + error.getMessage()); }
        }
        properties.setProperty(KEY, Integer.toString(currentDistance));
        properties.setProperty(THREADS_KEY, Integer.toString(serviceThreads));
        properties.setProperty(ENABLED_KEY, Boolean.toString(enabled));
        properties.setProperty(RENDERER_KEY, Boolean.toString(rendererEnabled));
        properties.setProperty(INGEST_KEY, Boolean.toString(terrainIngestionEnabled));
        properties.setProperty(SEAM_DEBUG_KEY, Boolean.toString(seamDebugOverlayEnabled));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) { properties.store(writer, "VoxyMobile Reborn client settings"); }
        } catch (IOException error) { System.err.println("[VoxyMobile] Could not save config: " + error.getMessage()); }
    }
}
