package dev.brezze.voxymobile.render.geometry;

public final class LodSeamCrackDetectorSmokeTest {
    public static void main(String[] args) {
        float[] terrain = {0,0,0, 10,0,0};
        float[] seam = {2,0,0, 5,0.05f,0, 8,0,0};
        var exact = LodSeamCrackDetector.inspect(seam, terrain, false, 0.051);
        check(exact.seamSamples() == 3, "sample count");
        check(exact.matchedSamples() == 3 && !exact.hasCracks(), "points on terrain polyline match");
        check(exact.maxGap() <= 0.051, "maximum gap is bounded by tolerance");

        float[] raised = {2,0.2f,0, 5,0.2f,0};
        var cracked = LodSeamCrackDetector.inspect(raised, terrain, false, 0.1);
        check(cracked.crackedSamples() == 2 && cracked.hasCracks(), "raised seam samples detected");
        check(Math.abs(cracked.meanGap() - 0.2) < 1e-6, "mean gap reported");

        float[] closed = {0,0,0, 1,0,0, 1,0,1};
        var closure = LodSeamCrackDetector.inspect(new float[]{0.5f,0,0.5f}, closed, true, 0.001);
        check(closure.matchedSamples() == 1, "closed polyline includes last-to-first segment");

        boolean badTolerance = false;
        try { LodSeamCrackDetector.inspect(seam, terrain, false, Double.NaN); }
        catch (IllegalArgumentException expected) { badTolerance = true; }
        check(badTolerance, "invalid tolerance rejected");
        boolean badCoordinates = false;
        try { LodSeamCrackDetector.inspect(new float[]{Float.NaN,0,0}, terrain, false, 1); }
        catch (IllegalArgumentException expected) { badCoordinates = true; }
        check(badCoordinates, "non-finite coordinates rejected");
        System.out.println("LodSeamCrackDetectorSmokeTest: PASS (8 assertions)");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
