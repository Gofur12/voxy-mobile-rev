package dev.brezze.voxymobile.render.geometry;

import java.util.Arrays;
import java.util.Collections;

public final class LodNeighborBatchSmokeTest {
    public static void main(String[] args) {
        LodNeighborDetector.Region west = new LodNeighborDetector.Region(0, 0, 16, 16, 0);
        LodNeighborDetector.Region east = new LodNeighborDetector.Region(16, 0, 32, 16, 1);
        LodNeighborDetector.Region north = new LodNeighborDetector.Region(0, 16, 16, 32, 0);
        LodNeighborDetector.Region far = new LodNeighborDetector.Region(64, 64, 80, 80, 2);

        LodNeighborBatch.Result result = LodNeighborBatch.build(Arrays.asList(west, east, north, far), 10);
        check(result.regionCount() == 4, "reports active region count");
        check(result.neighborCount() == 2, "finds each touching pair once");
        check(result.seamCandidateCount() == 1, "counts only LOD-mismatched neighbors");
        check(!result.truncated(), "complete scan is not truncated");
        check(result.candidates().get(0).firstRegionIndex == 0
                && result.candidates().get(0).secondRegionIndex == 1, "stable input pair indices");
        check(result.candidates().get(0).requiresSeam(), "LOD mismatch becomes seam candidate");
        check(!result.candidates().get(1).requiresSeam(), "equal LOD adjacency is not a seam candidate");
        expectFailure(() -> result.candidates().clear());

        LodNeighborBatch.Result capped = LodNeighborBatch.build(Arrays.asList(west, east, north), 1);
        check(capped.neighborCount() == 1 && capped.truncated(), "cap marks incomplete result");
        LodNeighborBatch.Result zeroCap = LodNeighborBatch.build(Arrays.asList(west, east), 0);
        check(zeroCap.neighborCount() == 0 && zeroCap.truncated(), "zero cap reports omitted candidate");
        check(LodNeighborBatch.build(Collections.emptyList(), 0).regionCount() == 0, "empty active set");
        expectFailure(() -> LodNeighborBatch.build(null, 1));
        expectFailure(() -> LodNeighborBatch.build(Arrays.asList(west, null), 1));
        expectFailure(() -> LodNeighborBatch.build(Arrays.asList(west), -1));
        System.out.println("LodNeighborBatchSmokeTest PASS (14 assertions)");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("expected IllegalArgumentException or UnsupportedOperationException"); }
        catch (IllegalArgumentException | UnsupportedOperationException expected) { }
    }
}
