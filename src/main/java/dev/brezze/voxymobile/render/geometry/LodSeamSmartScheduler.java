package dev.brezze.voxymobile.render.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Visibility- and lifecycle-aware deterministic scheduler for CPU seam builds. */
public final class LodSeamSmartScheduler {
    private LodSeamSmartScheduler() {}

    public record Candidate(LodSeamKey key, double distanceSquared, boolean visible,
                            boolean active, long revision) {
        public Candidate {
            if (key == null) throw new IllegalArgumentException("key is required");
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0)
                throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
        }
    }

    /** Returns unique active, visible candidates, nearest first, bounded by maxItems. */
    public static List<Candidate> select(List<Candidate> candidates, int maxItems) {
        if (candidates == null) throw new IllegalArgumentException("candidates are null");
        if (maxItems < 0) throw new IllegalArgumentException("maxItems must be non-negative");
        Map<LodSeamKey, Candidate> newestByKey = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate == null || !candidate.active() || !candidate.visible()) continue;
            Candidate previous = newestByKey.get(candidate.key());
            if (previous == null || candidate.revision() > previous.revision()
                    || (candidate.revision() == previous.revision()
                    && candidate.distanceSquared() < previous.distanceSquared())) {
                newestByKey.put(candidate.key(), candidate);
            }
        }
        List<Candidate> eligible = new ArrayList<>(newestByKey.values());
        eligible.sort(Comparator.comparingDouble(Candidate::distanceSquared)
                .thenComparingLong(c -> c.key().firstRegionId())
                .thenComparingLong(c -> c.key().secondRegionId()));
        if (eligible.size() > maxItems) return List.copyOf(eligible.subList(0, maxItems));
        return List.copyOf(eligible);
    }
}
