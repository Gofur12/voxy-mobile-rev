package dev.brezze.voxymobile.render.geometry;

public final class LodSeamPatchGeneratorSmokeTest {
    public static void main(String[] args) {
        float[] fineMesh = packed(
                4,0,9, 4,1,9, 4,2,9, 4,3,9,
                3,0,9, 3,1,9, 3,2,9, 3,3,9);
        float[] coarseMesh = packed(
                4,0,9, 4,3,9,
                5,0,9, 5,3,9);
        LodSeamPatchGenerator.EdgeQuery fine = new LodSeamPatchGenerator.EdgeQuery(0,4,1,2,9,0.0001f);
        LodSeamPatchGenerator.EdgeQuery coarse = new LodSeamPatchGenerator.EdgeQuery(0,4,1,2,9,0.0001f);
        LodSeamPatchData patch = LodSeamPatchGenerator.generate(
                fineMesh, fine, coarseMesh, coarse, 0.3f, 0.5f, 0.7f, 1.0f);
        check(patch.vertexCount() == 6, "extracts four fine and two coarse points");
        check(patch.triangleCount() == 4, "builds zipper triangles automatically");
        check(patch.copyIndices().length == 12, "index payload length");
        check(patch.byteSize() > 0, "upload payload has byte size");
        expectFailure(() -> LodSeamPatchGenerator.generate(fineMesh, fine, packed(8,0,9,8,3,9), coarse, 1,1,1,1));
        expectFailure(() -> LodSeamPatchGenerator.generate(fineMesh, fine, packed(8,0,9,8,3,9),
                new LodSeamPatchGenerator.EdgeQuery(0,8,1,2,9,0.001f), 1,1,1,1));
        expectFailure(() -> LodSeamPatchGenerator.generate(null, fine, coarseMesh, coarse, 1,1,1,1));
        System.out.println("LodSeamPatchGeneratorSmokeTest PASS (7 assertions)");
    }

    private static float[] packed(float... xyz) {
        float[] result = new float[(xyz.length / 3) * 7];
        for (int i = 0, o = 0; i < xyz.length; i += 3) {
            result[o++] = xyz[i]; result[o++] = xyz[i+1]; result[o++] = xyz[i+2];
            result[o++] = 1; result[o++] = 1; result[o++] = 1; result[o++] = 1;
        }
        return result;
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static void expectFailure(Runnable r) {
        try { r.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
