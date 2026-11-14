package dev.brezze.voxymobile.render.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a deterministic neighbor list from one frame's selected horizontal LOD regions.
 * This is a CPU bridge: callers pass the active selection explicitly; it does not read the
 * renderer's private node set or perform mesh extraction/GLES work.
 */
public final class LodNeighborBatch {
    private LodNeighborBatch() {}

    public static final class Candidate {
        public final int firstRegionIndex;
        public final int secondRegionIndex;
        public final LodNeighborDetector.NeighborPair neighbor;

        private Candidate(int first, int second, LodNeighborDetector.NeighborPair neighbor) {
            this.firstRegionIndex = first;
            this.secondRegionIndex = second;
            this.neighbor = neighbor;
        }

        public boolean requiresSeam() { return neighbor.requiresSeam(); }
    }

    public static final class Result {
        private final List<Candidate> candidates;
        private final int regionCount;
        private final int seamCandidateCount;
        private final boolean truncated;

        private Result(List<Candidate> candidates, int regionCount, int seamCandidateCount, boolean truncated) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.regionCount = regionCount;
            this.seamCandidateCount = seamCandidateCount;
            this.truncated = truncated;
        }

        public List<Candidate> candidates() { return candidates; }
        public int regionCount() { return regionCount; }
        public int neighborCount() { return candidates.size(); }
        public int seamCandidateCount() { return seamCandidateCount; }
        public boolean truncated() { return truncated; }
    }

    /**
     * Checks each unordered pair once. maxCandidates bounds retained result memory; if reached,
     * scanning stops and truncated() is true so callers never mistake a partial list for complete.
     */
    public static Result build(List<LodNeighborDetector.Region> activeRegions, int maxCandidates) {
        if (activeRegions == null) throw new IllegalArgumentException("active regions must not be null");
        if (maxCandidates < 0) throw new IllegalArgumentException("maxCandidates must be >= 0");
        for (LodNeighborDetector.Region region : activeRegions)
            if (region == null) throw new IllegalArgumentException("active regions must not contain null");

        List<Candidate> found = new ArrayList<>();
        int seams = 0;
        for (int i = 0; i < activeRegions.size(); i++) {
            for (int j = i + 1; j < activeRegions.size(); j++) {
                LodNeighborDetector.NeighborPair pair = LodNeighborDetector.detect(
                        activeRegions.get(i), activeRegions.get(j));
                if (pair == null) continue;
                if (found.size() >= maxCandidates)
                    return new Result(found, activeRegions.size(), seams, true);
                found.add(new Candidate(i, j, pair));
                if (pair.requiresSeam()) seams++;
            }
        }
        return new Result(found, activeRegions.size(), seams, false);
    }
}
