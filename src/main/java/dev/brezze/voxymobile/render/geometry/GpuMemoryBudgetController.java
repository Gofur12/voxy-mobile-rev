package dev.brezze.voxymobile.render.geometry;

/** CPU-only hysteretic GPU allocation budget policy for GLES mesh lifecycle management. */
public final class GpuMemoryBudgetController {
    public static final long DEFAULT_BUDGET_BYTES = 64L * 1024L * 1024L;
    private static final double EVICTION_START_RATIO = 0.90;
    private static final double EVICTION_TARGET_RATIO = 0.78;
    private final long budgetBytes;
    private boolean evictionActive;

    public GpuMemoryBudgetController() { this(DEFAULT_BUDGET_BYTES); }

    public GpuMemoryBudgetController(long budgetBytes) {
        if (budgetBytes <= 0) throw new IllegalArgumentException("budgetBytes must be positive");
        this.budgetBytes = budgetBytes;
    }

    /** Starts eviction at the high-water mark and keeps it active until the lower target is reached. */
    public synchronized boolean shouldEvict(long allocatedBytes) {
        if (allocatedBytes < 0) throw new IllegalArgumentException("allocatedBytes must be non-negative");
        if (!evictionActive && allocatedBytes > threshold(EVICTION_START_RATIO)) evictionActive = true;
        if (evictionActive && allocatedBytes <= threshold(EVICTION_TARGET_RATIO)) evictionActive = false;
        return evictionActive;
    }

    /** Target used while eviction is active; leaves headroom for uploads arriving next frame. */
    public long evictionTargetBytes() { return threshold(EVICTION_TARGET_RATIO); }
    public long budgetBytes() { return budgetBytes; }
    public synchronized boolean evictionActive() { return evictionActive; }

    /** Returns a bounded pressure level for diagnostics and future adaptive upload policies. */
    public int pressureLevel(long allocatedBytes) {
        if (allocatedBytes < 0) throw new IllegalArgumentException("allocatedBytes must be non-negative");
        double ratio = allocatedBytes / (double) budgetBytes;
        if (ratio >= 1.0) return 3;
        if (ratio >= 0.90) return 2;
        if (ratio >= 0.75) return 1;
        return 0;
    }

    private long threshold(double ratio) { return Math.max(1L, (long) (budgetBytes * ratio)); }
}
