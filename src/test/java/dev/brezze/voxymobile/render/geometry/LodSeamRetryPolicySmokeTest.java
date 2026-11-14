package dev.brezze.voxymobile.render.geometry;

/** Deterministic CPU contract tests for revision-aware seam retry backoff. */
public final class LodSeamRetryPolicySmokeTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void bad(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }
    public static void main(String[] args) {
        LodSeamRetryPolicy policy = new LodSeamRetryPolicy();
        LodSeamKey key = new LodSeamKey(10, 20);
        long t = 1_000_000_000L;

        check(policy.shouldAttempt(key, 1, t), "first attempt is immediately allowed");
        policy.recordFailure(key, 1, t);
        check(policy.attempts(key, 1) == 1, "first failure is recorded");
        check(!policy.shouldAttempt(key, 1, t + LodSeamRetryPolicy.BASE_DELAY_NANOS - 1),
                "first retry is blocked just before 250ms");
        check(policy.shouldAttempt(key, 1, t + LodSeamRetryPolicy.BASE_DELAY_NANOS),
                "first retry is allowed at 250ms");

        long secondFailureAt = t + LodSeamRetryPolicy.BASE_DELAY_NANOS;
        policy.recordFailure(key, 1, secondFailureAt);
        check(policy.attempts(key, 1) == 2, "second failure increments attempts");
        check(!policy.shouldAttempt(key, 1, secondFailureAt + 499_999_999L),
                "second retry is blocked just before 500ms");
        check(policy.shouldAttempt(key, 1, secondFailureAt + 500_000_000L),
                "second retry is allowed at 500ms");

        check(policy.shouldAttempt(key, 2, t), "new source revision resets backoff immediately");
        policy.recordFailure(key, 2, t);
        policy.recordSuccess(key, 2);
        check(policy.attempts(key, 2) == 0, "success clears retry history");
        check(policy.shouldAttempt(key, 2, t), "successful revision can be scheduled again if needed");

        policy.recordFailure(key, 2, t);
        check(policy.remove(key), "key lifecycle can be removed");
        check(policy.size() == 0, "remove clears policy state");
        policy.recordFailure(key, 3, t);
        policy.clear();
        check(policy.size() == 0, "clear removes all retry state");

        bad(() -> policy.shouldAttempt(null, 1, t));
        bad(() -> policy.recordFailure(key, -1, t));
        System.out.println("LodSeamRetryPolicySmokeTest PASS (" + assertions + " assertions)");
    }
}
