package dev.brezze.voxymobile.render.geometry;

import java.util.HashMap;
import java.util.Map;

/**
 * Assigns a monotonically increasing upload revision to each seam key whenever either
 * source mesh revision changes. This avoids collisions caused by adding two revisions
 * together and lets the upload queue suppress duplicate work between refresh passes.
 */
public final class LodSeamRevisionTracker {
    private final Map<LodSeamKey, State> states = new HashMap<>();

    /** Returns the stable revision for an unchanged source pair, or advances it on change. */
    public synchronized long revisionFor(LodSeamKey key, long fineRevision, long coarseRevision) {
        if (key == null) throw new IllegalArgumentException("key is null");
        if (fineRevision < 0 || coarseRevision < 0)
            throw new IllegalArgumentException("source revisions must be non-negative");
        State old = states.get(key);
        if (old != null && old.fineRevision == fineRevision && old.coarseRevision == coarseRevision)
            return old.uploadRevision;
        long next = old == null ? 1L : Math.addExact(old.uploadRevision, 1L);
        states.put(key, new State(fineRevision, coarseRevision, next));
        return next;
    }

    public synchronized boolean remove(LodSeamKey key) { return states.remove(key) != null; }
    public synchronized long currentRevision(LodSeamKey key) {
        State state = states.get(key);
        return state == null ? -1L : state.uploadRevision;
    }
    public synchronized void clear() { states.clear(); }
    public synchronized int size() { return states.size(); }

    private record State(long fineRevision, long coarseRevision, long uploadRevision) {}
}
