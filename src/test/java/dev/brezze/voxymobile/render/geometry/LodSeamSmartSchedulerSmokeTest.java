package dev.brezze.voxymobile.render.geometry;

import java.util.List;

public final class LodSeamSmartSchedulerSmokeTest {
    public static void main(String[] args) {
        LodSeamKey near = new LodSeamKey(1, 2), far = new LodSeamKey(3, 4);
        List<LodSeamSmartScheduler.Candidate> input = List.of(
                new LodSeamSmartScheduler.Candidate(far, 100, true, true, 1),
                new LodSeamSmartScheduler.Candidate(near, 4, true, true, 2),
                new LodSeamSmartScheduler.Candidate(new LodSeamKey(5, 6), 1, false, true, 1),
                new LodSeamSmartScheduler.Candidate(new LodSeamKey(7, 8), 0, true, false, 1),
                new LodSeamSmartScheduler.Candidate(near, 2, true, true, 3));
        List<LodSeamSmartScheduler.Candidate> selected = LodSeamSmartScheduler.select(input, 4);
        check(selected.size() == 2, "inactive and invisible candidates excluded; duplicate key collapsed");
        check(selected.get(0).key().equals(near) && selected.get(0).revision() == 3, "newest duplicate revision retained before distance ordering");
        check(selected.get(1).key().equals(far), "farther visible active candidate retained");
        check(LodSeamSmartScheduler.select(input, 0).isEmpty(), "zero budget");
        check(LodSeamSmartScheduler.select(input, 10).size() == 2, "budget above eligible count");
        boolean rejected = false;
        try { new LodSeamSmartScheduler.Candidate(near, Double.NaN, true, true, 1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "non-finite distance rejected");
        System.out.println("LodSeamSmartSchedulerSmokeTest: PASS (6 assertions)");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
