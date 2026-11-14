package dev.brezze.voxymobile.render.streaming;

import dev.brezze.voxymobile.render.quality.AdaptiveLodQualityController;

/**
 * Mobile-oriented adaptive streaming policy inspired by Voxy's separation of
 * render-distance tracking, prioritized mesh generation and bounded geometry
 * management. This class is CPU-only and safe to unit test without Minecraft.
 */
public final class AdaptiveLodStreamingController {
    public static final long DEFAULT_GPU_BUDGET_BYTES = 64L * 1024L * 1024L;
    private static final double[] BASE_BOUNDARIES = {56.0, 112.0, 192.0};
    private static final double HYSTERESIS = 8.0;
    private static final double PREDICTION_SECONDS = 0.35;

    private double previousX;
    private double previousY;
    private double previousZ;
    private long previousNanos;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double speed;
    private boolean initialized;
    private final AdaptiveLodQualityController quality = new AdaptiveLodQualityController();

    /** Updates camera velocity using a clamped frame interval to reject pause/resume spikes. */
    public void update(double x, double y, double z, long nowNanos) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        if (!initialized || nowNanos <= previousNanos) {
            previousX = x; previousY = y; previousZ = z;
            previousNanos = nowNanos;
            velocityX = velocityY = velocityZ = speed = 0.0;
            initialized = true;
            return;
        }
        double dt = (nowNanos - previousNanos) / 1_000_000_000.0;
        previousNanos = nowNanos;
        double dx = x - previousX, dy = y - previousY, dz = z - previousZ;
        previousX = x; previousY = y; previousZ = z;
        if (dt < 0.001 || dt > 0.5) {
            velocityX = velocityY = velocityZ = speed = 0.0;
            return;
        }
        double invDt = 1.0 / dt;
        // Smooth velocity so tiny camera jitter does not constantly change the LOD policy.
        double alpha = 0.35;
        velocityX += (dx * invDt - velocityX) * alpha;
        velocityY += (dy * invDt - velocityY) * alpha;
        velocityZ += (dz * invDt - velocityZ) * alpha;
        speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    }

    /** Faster movement moves LOD boundaries inward, reducing expensive near-detail rebuilds. */
    public int selectLod(double distanceSquared, int previousLod) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0)
            throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
        if (previousLod < -1 || previousLod > 3)
            throw new IllegalArgumentException("previousLod must be -1..3");
        double speedFactor = clamp(speed / 28.0, 0.0, 1.0);
        double scale = (1.0 - 0.35 * speedFactor) * quality.lodDistanceScale();
        double distance = Math.sqrt(distanceSquared);
        double b0 = BASE_BOUNDARIES[0] * scale;
        double b1 = BASE_BOUNDARIES[1] * scale;
        double b2 = BASE_BOUNDARIES[2] * scale;
        double h = HYSTERESIS * scale;
        if (previousLod == 0 && distance < b0 + h) return 0;
        if (previousLod == 1 && distance >= b0 - h && distance < b1 + h) return 1;
        if (previousLod == 2 && distance >= b1 - h && distance < b2 + h) return 2;
        if (previousLod == 3 && distance >= b2 - h) return 3;
        if (distance >= b2) return 3;
        if (distance >= b1) return 2;
        if (distance >= b0) return 1;
        return 0;
    }

    /** Predicts the short-term camera position for Voxy-style movement-aware task prioritization. */
    public double predictedDistanceSquared(double centerX, double centerY, double centerZ,
                                           double cameraX, double cameraY, double cameraZ) {
        double px = cameraX + velocityX * PREDICTION_SECONDS;
        double py = cameraY + velocityY * PREDICTION_SECONDS;
        double pz = cameraZ + velocityZ * PREDICTION_SECONDS;
        double dx = centerX - px, dy = centerY - py, dz = centerZ - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Upload more while stationary; limit uploads during fast traversal to protect frame time. */
    public int uploadsPerFrame(int completedQueueSize) {
        if (completedQueueSize <= 0) return 0;
        int motionBudget = speed >= 12.0 ? 1 : speed >= 4.0 ? 2 : 3;
        return Math.min(Math.min(motionBudget, quality.maxUploadsPerFrame()), completedQueueSize);
    }

    /**
     * Keeps a small independent budget for coarse parent generation. Parent meshes
     * establish far-field coverage, so they should not be starved by leaf refinement;
     * fast camera movement and degraded frame-time tiers still reduce CPU pressure.
     */
    public int maxParentBuildsPerFrame() {
        int qualityBudget = switch (quality.qualityTier()) {
            case 2, 3 -> 1;
            default -> 2;
        };
        int motionBudget = speed >= 12.0 ? 1 : speed >= 4.0 ? 2 : 2;
        return Math.min(qualityBudget, motionBudget);
    }

    /** Feed the measured frame interval to the adaptive quality policy. */
    public void recordFrameTime(double frameMillis) { quality.recordFrameTime(frameMillis); }
    public int maxNewBuildsPerFrame() { return quality.maxNewBuildsPerFrame(); }
    public int qualityTier() { return quality.qualityTier(); }
    public double averageFrameMillis() { return quality.averageFrameMillis(); }
    public double speedBlocksPerSecond() { return speed; }
    public double velocityX() { return velocityX; }
    public double velocityY() { return velocityY; }
    public double velocityZ() { return velocityZ; }
    public long gpuBudgetBytes() { return DEFAULT_GPU_BUDGET_BYTES; }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
