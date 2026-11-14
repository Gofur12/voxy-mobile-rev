package dev.brezze.voxymobile.core.config;

public final class GpuMemoryBudgetPolicy {
    public static final int DEFAULT_MB = 64;
    public static final int MIN_MB = 32;
    public static final int MAX_MB = 256;
    private GpuMemoryBudgetPolicy() {}
    public static int normalize(int value) {
        int clamped = Math.max(MIN_MB, Math.min(MAX_MB, value));
        return MIN_MB + (int)Math.round((clamped - MIN_MB) / 16.0) * 16;
    }
    public static int parse(String value) {
        if (value == null) return DEFAULT_MB;
        try { return normalize(Integer.parseInt(value.trim())); }
        catch (RuntimeException ignored) { return DEFAULT_MB; }
    }
}
