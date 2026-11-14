package dev.brezze.voxymobile.render.quality;

/**
 * Frame-time feedback policy for mobile LOD quality. CPU-only: callers feed measured
 * frame times and use the returned limits when scheduling builds, selecting LOD bands,
 * and draining completed uploads. No GL or Minecraft API is touched here.
 */
public final class AdaptiveLodQualityController {
    public static final double DEFAULT_TARGET_FRAME_MILLIS = 16.6667;
    private static final double EMA_ALPHA = 0.12;
    private static final int SLOW_FRAMES_TO_DOWNGRADE = 8;
    private static final int FAST_FRAMES_TO_UPGRADE = 90;
    private static final double DOWNGRADE_RATIO = 1.12;
    private static final double UPGRADE_RATIO = 0.78;

    private final double targetFrameMillis;
    private double averageFrameMillis;
    private int qualityTier;
    private int slowFrames;
    private int fastFrames;
    private long samples;

    public AdaptiveLodQualityController() { this(DEFAULT_TARGET_FRAME_MILLIS); }

    public AdaptiveLodQualityController(double targetFrameMillis) {
        if (!Double.isFinite(targetFrameMillis) || targetFrameMillis < 5.0 || targetFrameMillis > 100.0)
            throw new IllegalArgumentException("target frame time must be 5..100 ms");
        this.targetFrameMillis = targetFrameMillis;
        this.averageFrameMillis = targetFrameMillis;
    }

    /** Feed one measured frame duration in milliseconds; invalid samples are ignored. */
    public void recordFrameTime(double frameMillis) {
        if (!Double.isFinite(frameMillis) || frameMillis <= 0.0 || frameMillis > 1000.0) return;
        if (samples == 0) averageFrameMillis = frameMillis;
        else averageFrameMillis += (frameMillis - averageFrameMillis) * EMA_ALPHA;
        samples++;

        if (averageFrameMillis > targetFrameMillis * DOWNGRADE_RATIO) {
            slowFrames++;
            fastFrames = 0;
            if (slowFrames >= SLOW_FRAMES_TO_DOWNGRADE) {
                qualityTier = Math.min(3, qualityTier + 1);
                slowFrames = 0;
            }
        } else if (averageFrameMillis < targetFrameMillis * UPGRADE_RATIO) {
            fastFrames++;
            slowFrames = 0;
            if (fastFrames >= FAST_FRAMES_TO_UPGRADE) {
                qualityTier = Math.max(0, qualityTier - 1);
                fastFrames = 0;
            }
        } else {
            slowFrames = 0;
            fastFrames = 0;
        }
    }

    /** Multiplies Stage 18 entry distances; lower values select coarser LOD sooner. */
    public double lodDistanceScale() {
        return switch (qualityTier) { case 1 -> 0.90; case 2 -> 0.78; case 3 -> 0.65; default -> 1.0; };
    }

    /** Upper bound for new mesh-generation requests initiated during one frame. */
    public int maxNewBuildsPerFrame() {
        return switch (qualityTier) { case 1 -> 3; case 2 -> 2; case 3 -> 1; default -> 4; };
    }

    /** Upper bound for completed CPU meshes uploaded to the GPU during one frame. */
    public int maxUploadsPerFrame() {
        return switch (qualityTier) { case 1 -> 2; case 2, 3 -> 1; default -> 3; };
    }

    public int qualityTier() { return qualityTier; }
    public double averageFrameMillis() { return averageFrameMillis; }
    public double targetFrameMillis() { return targetFrameMillis; }
    public long sampleCount() { return samples; }

    /** Reset feedback after world changes or a renderer lifecycle restart. */
    public void reset() {
        averageFrameMillis = targetFrameMillis;
        qualityTier = slowFrames = fastFrames = 0;
        samples = 0;
    }
}
