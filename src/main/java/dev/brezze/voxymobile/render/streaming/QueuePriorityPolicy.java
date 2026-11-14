package dev.brezze.voxymobile.render.streaming;

/** CPU-only bounded priority aging shared by leaf and parent mesh queues. */
public final class QueuePriorityPolicy {
    private static final double AGING_RATE = 0.20;
    private static final long MAX_AGE_NANOS = 120_000_000_000L;

    /** Lower scores run first. Aging is capped to avoid old work overwhelming fresh visible work. */
    public double effectivePriority(double basePriority, long enqueuedNanos, long nowNanos) {
        if (!Double.isFinite(basePriority) || basePriority < 0.0) return Double.MAX_VALUE;
        long age = nowNanos <= enqueuedNanos ? 0L : Math.min(nowNanos - enqueuedNanos, MAX_AGE_NANOS);
        double seconds = age / 1_000_000_000.0;
        return basePriority / (1.0 + AGING_RATE * seconds);
    }
}
