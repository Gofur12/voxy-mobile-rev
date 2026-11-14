package dev.brezze.voxymobile.core.mesh;

public final class LodSeamStitcherSmokeTest {
    public static void main(String[] args) {
        float[] fine = {0,0,0, 1,0,0, 2,0,0, 3,0,0, 4,0,0};
        float[] coarse = {0,0,0, 2,0.25f,0, 4,0,0};
        int[] indices = LodSeamStitcher.stitch(fine, coarse);
        if (indices.length != (5 + 3 - 2) * 3) throw new AssertionError("wrong triangle index count");
        if (indices.length / 3 != 6) throw new AssertionError("expected 6 zipper triangles");
        for (int index : indices) if (index < 0 || index >= 8) throw new AssertionError("index out of range");

        float[] equalA = {0,0,0, 1,0,0, 2,0,0};
        float[] equalB = {0,0,0, 1,1,0, 2,0,0};
        if (LodSeamStitcher.stitch(equalA, equalB).length != 12)
            throw new AssertionError("equal-resolution seam must produce two triangles per segment");

        expectFailure(() -> LodSeamStitcher.stitch(new float[]{0,0,0}, coarse));
        expectFailure(() -> LodSeamStitcher.stitch(fine, new float[]{1,0,0, 2,0,0}));
        expectFailure(() -> LodSeamStitcher.stitch(new float[]{0,0,0, Float.NaN,0,0}, new float[]{0,0,0, 1,0,0}));
        System.out.println("LodSeamStitcherSmokeTest PASS (6 assertions)");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
