package dev.brezze.voxymobile.render.geometry;

public final class LodSeamPatchDataSmokeTest {
    public static void main(String[] args) {
        float[] fine = {0,0,0, 1,0,0, 2,0,0, 3,0,0, 4,0,0};
        float[] coarse = {0,0,0, 2,0.25f,0, 4,0,0};
        LodSeamPatchData patch = LodSeamPatchData.build(fine, coarse, 0.2f, 0.4f, 0.6f, 1.0f);
        check(patch.vertexCount() == 8, "packed vertex count");
        check(patch.indexCount() == 18, "zipper index count");
        check(patch.triangleCount() == 6, "triangle count");
        float[] vertices = patch.copyVertices();
        check(vertices.length == 8 * 7, "seven-float renderer layout");
        check(vertices[3] == 0.2f && vertices[4] == 0.4f && vertices[5] == 0.6f && vertices[6] == 1.0f, "RGBA packing");
        int[] indices = patch.copyIndices();
        for (int index : indices) check(index >= 0 && index < patch.vertexCount(), "index range");
        long expectedBytes = (long) vertices.length * Float.BYTES + (long) indices.length * Integer.BYTES;
        check(patch.byteSize() == expectedBytes, "upload byte size");
        vertices[0] = 999;
        check(patch.copyVertices()[0] == 0, "defensive vertex copy");
        expectFailure(() -> LodSeamPatchData.build(fine, coarse, 2, 0, 0, 1));
        expectFailure(() -> LodSeamPatchData.build(fine, new float[]{1,0,0, 2,0,0}, 0, 0, 0, 1));
        System.out.println("LodSeamPatchDataSmokeTest PASS (payload checks)");
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static void expectFailure(Runnable r) {
        try { r.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
