package dev.brezze.voxymobile.render.streaming;

/** Dependency-free checks for Stage 22 queue aging. */
public final class QueuePriorityPolicySmokeTest {
    public static void main(String[] args) {
        QueuePriorityPolicy policy = new QueuePriorityPolicy();
        long now = 50_000_000_000L;
        double freshNear = policy.effectivePriority(100.0, now, now);
        double agedFar = policy.effectivePriority(180.0, now - 20_000_000_000L, now);
        if (freshNear != 100.0) throw new AssertionError("fresh priority must remain unchanged");
        if (!(agedFar < 180.0)) throw new AssertionError("queued work must age");
        double agedInvalidClock = policy.effectivePriority(42.0, now + 1_000_000L, now);
        if (agedInvalidClock != 42.0) throw new AssertionError("future enqueue time must not create negative age");
        if (policy.effectivePriority(Double.NaN, now, now) != Double.MAX_VALUE)
            throw new AssertionError("NaN priority must be rejected");
        if (policy.effectivePriority(-1.0, now, now) != Double.MAX_VALUE)
            throw new AssertionError("negative priority must be rejected");
        double capped = policy.effectivePriority(100.0, now - 300_000_000_000L, now);
        double capExpected = 100.0 / (1.0 + 0.20 * 120.0);
        if (Math.abs(capped - capExpected) > 1.0e-9) throw new AssertionError("aging cap mismatch");
        System.out.println("QueuePriorityPolicySmokeTest: PASS (6 checks)");
    }
}
