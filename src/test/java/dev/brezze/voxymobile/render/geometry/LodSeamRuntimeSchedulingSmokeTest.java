package dev.brezze.voxymobile.render.geometry;

import java.util.List;

/** CPU-only regression test for the runtime scheduler's visibility, revision and budget rules. */
public final class LodSeamRuntimeSchedulingSmokeTest {
    public static void main(String[] args) {
        LodSeamKey keyA = new LodSeamKey(10, 11);
        LodSeamKey keyB = new LodSeamKey(20, 21);
        LodSeamKey keyC = new LodSeamKey(30, 31);
        List<LodSeamSmartScheduler.Candidate> candidates = List.of(
                new LodSeamSmartScheduler.Candidate(keyA, 1.0, true, true, 4),
                new LodSeamSmartScheduler.Candidate(keyA, 9.0, true, true, 5),
                new LodSeamSmartScheduler.Candidate(keyB, 2.0, false, true, 9),
                new LodSeamSmartScheduler.Candidate(keyC, 3.0, true, false, 9));
        List<LodSeamSmartScheduler.Candidate> selected = LodSeamSmartScheduler.select(candidates, 8);
        check(selected.size() == 1, "only active visible unique seam remains");
        check(selected.get(0).key().equals(keyA), "correct active seam selected");
        check(selected.get(0).revision() == 5, "newer revision wins even when farther");
        check(selected.get(0).distanceSquared() == 9.0, "distance belongs to selected revision");
        check(LodSeamSmartScheduler.select(candidates, 0).isEmpty(), "zero per-refresh build budget");
        check(LodSeamSmartScheduler.select(candidates, 1).size() == 1, "bounded runtime build budget");
        System.out.println("LodSeamRuntimeSchedulingSmokeTest: PASS (6 assertions)");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
