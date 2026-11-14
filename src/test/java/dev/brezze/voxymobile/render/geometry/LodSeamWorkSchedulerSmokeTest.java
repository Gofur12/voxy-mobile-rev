package dev.brezze.voxymobile.render.geometry;

public final class LodSeamWorkSchedulerSmokeTest {
    public static void main(String[] args) {
        int assertions = 0;
        int[] selected = LodSeamWorkScheduler.prioritize(new double[]{100, 4, 25, 4}, 3);
        check(selected.length == 3, "cap respected"); assertions++;
        check(selected[0] == 1 && selected[1] == 3 && selected[2] == 2, "nearest-first stable tie order"); assertions++;
        check(LodSeamWorkScheduler.prioritize(new double[]{1, 2}, 0).length == 0, "zero budget"); assertions++;
        check(LodSeamWorkScheduler.prioritize(new double[]{1, 2}, 8).length == 2, "budget above work count"); assertions++;
        boolean rejected = false;
        try { LodSeamWorkScheduler.prioritize(new double[]{Double.NaN}, 1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "NaN rejected"); assertions++;
        System.out.println("LodSeamWorkSchedulerSmokeTest: PASS (" + assertions + " assertions)");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
