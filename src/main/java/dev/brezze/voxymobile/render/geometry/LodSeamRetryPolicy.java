package dev.brezze.voxymobile.render.geometry;

import java.util.HashMap;
import java.util.Map;

/**
 * Revision-aware exponential backoff for failed asynchronous LOD seam builds.
 * Time values are supplied by the caller so policy behavior is deterministic in tests.
 */
public final class LodSeamRetryPolicy {
    public static final long BASE_DELAY_NANOS = 250_000_000L;
    public static final long MAX_DELAY_NANOS = 8_000_000_000L;
    private static final int MAX_ATTEMPTS = 30;

    private final Map<LodSeamKey, State> states = new HashMap<>();

    /** True when this revision has not failed, or its retry delay has elapsed. */
    public synchronized boolean shouldAttempt(LodSeamKey key, long revision, long nowNanos) {
        validate(key, revision);
        State state = states.get(key);
        if (state == null || state.revision != revision) return true;
        return nowNanos - state.nextAllowedNanos >= 0L;
    }

    /** Records a failed build and schedules the next attempt with capped exponential backoff. */
    public synchronized void recordFailure(LodSeamKey key, long revision, long nowNanos) {
        validate(key, revision);
        State old = states.get(key);
        int attempts = old == null || old.revision != revision
                ? 1 : Math.min(MAX_ATTEMPTS, old.attempts + 1);
        long delay = BASE_DELAY_NANOS;
        for (int i = 1; i < attempts && delay < MAX_DELAY_NANOS; i++)
            delay = Math.min(MAX_DELAY_NANOS, delay * 2L);
        long deadline = nowNanos > Long.MAX_VALUE - delay ? Long.MAX_VALUE : nowNanos + delay;
        states.put(key, new State(revision, attempts, deadline));
    }

    /** Clears retry history only for the successfully generated revision. */
    public synchronized void recordSuccess(LodSeamKey key, long revision) {
        validate(key, revision);
        State state = states.get(key);
        if (state != null && state.revision == revision) states.remove(key);
    }

    public synchronized boolean remove(LodSeamKey key) { return states.remove(key) != null; }
    public synchronized void clear() { states.clear(); }
    public synchronized int size() { return states.size(); }
    public synchronized int attempts(LodSeamKey key, long revision) {
        State state = states.get(key);
        return state == null || state.revision != revision ? 0 : state.attempts;
    }

    private static void validate(LodSeamKey key, long revision) {
        if (key == null) throw new IllegalArgumentException("key is null");
        if (revision < 0L) throw new IllegalArgumentException("revision must be non-negative");
    }

    private record State(long revision, int attempts, long nextAllowedNanos) {}
}
