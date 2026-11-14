package dev.brezze.voxymobile.core.config;

/** Pure policy for the independent LOD radius; safe to test without Minecraft/Fabric runtime. */
public final class LodDistancePolicy {
    public static final int DEFAULT_DISTANCE = 4096;
    public static final int MIN_DISTANCE = 256;
    public static final int MAX_DISTANCE = 8192;

    private LodDistancePolicy() {}

    public static int normalize(int distance) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }

    public static int parse(String value) {
        if (value == null) return DEFAULT_DISTANCE;
        try {
            return normalize(Integer.parseInt(value.trim()));
        } catch (RuntimeException ignored) {
            return DEFAULT_DISTANCE;
        }
    }
}
