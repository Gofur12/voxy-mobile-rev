package dev.brezze.voxymobile.render.geometry;

/** Focused CPU test for live terrain-boundary extraction and seam payload production. */
public final class LodSeamLiveProducerSmokeTest {
    public static void main(String[] args) {
        LodSeamKey key = new LodSeamKey(11L, 22L);
        Mesh fine = strip(new float[]{1.0f, 1.6f, 1.0f}, 0.2f, 0.4f, 0.6f);
        Mesh coarse = strip(new float[]{1.0f, 1.0f}, 0.8f, 0.7f, 0.3f);
        LodSeamLiveProducer.Result result = LodSeamLiveProducer.generate(key, 7L,
                fine.vertices, fine.indices, coarse.vertices, coarse.indices,
                0, 0.0f, 1, 0.0f, 2.0f, 2, 0.0f, 1.0f, 1.0e-3f, 64);
        check(result.generated(), "compatible fine/coarse boundaries generate payload: " + result.status());
        check(result.payload().key().equals(key), "payload retains canonical pair key");
        check(result.payload().contentRevision() == 7L, "payload retains revision");
        check(result.payload().indexCount() >= 6, "zipper emits indexed triangles");
        float[] payloadVertices = result.payload().copyVertices();
        check(hasColor(payloadVertices, 0.2f, 0.4f, 0.6f), "fine-side source color is preserved");
        check(hasColor(payloadVertices, 0.8f, 0.7f, 0.3f), "coarse-side source color is preserved");
        int[] payloadIndices = result.payload().copyIndices();
        check(payloadIndices.length % 3 == 0, "indices remain triangle-aligned");
        check(allIndicesInRange(payloadIndices, result.payload().vertexCount()), "all zipper indices address packed vertices");
        check(allTrianglesNonDegenerate(payloadVertices, payloadIndices), "zero-area triangles are filtered");

        LodSeamLiveProducer.Result missing = LodSeamLiveProducer.generate(key, 8L,
                fine.vertices, fine.indices, fine.vertices, fine.indices,
                0, 50.0f, 1, 0.0f, 2.0f, 2, 0.0f, 1.0f, 1.0e-3f, 64);
        check(!missing.generated() && "boundary-missing".equals(missing.status()), "missing plane boundary is rejected cleanly");
        System.out.println("LodSeamLiveProducerSmokeTest PASS (10 assertions)");
    }

    private static boolean hasColor(float[] vertices, float r, float g, float b) {
        for (int i = 0; i < vertices.length; i += 7)
            if (Math.abs(vertices[i + 3] - r) < 1e-5f && Math.abs(vertices[i + 4] - g) < 1e-5f
                    && Math.abs(vertices[i + 5] - b) < 1e-5f) return true;
        return false;
    }
    private static boolean allIndicesInRange(int[] indices, int count) {
        for (int index : indices) if (index < 0 || index >= count) return false;
        return true;
    }
    private static boolean allTrianglesNonDegenerate(float[] v, int[] ix) {
        for (int i = 0; i < ix.length; i += 3) {
            int a = ix[i] * 7, b = ix[i + 1] * 7, c = ix[i + 2] * 7;
            double abx=v[b]-v[a], aby=v[b+1]-v[a+1], abz=v[b+2]-v[a+2];
            double acx=v[c]-v[a], acy=v[c+1]-v[a+1], acz=v[c+2]-v[a+2];
            double nx=aby*acz-abz*acy, ny=abz*acx-abx*acz, nz=abx*acy-aby*acx;
            if (nx*nx+ny*ny+nz*nz <= 1e-12) return false;
        }
        return true;
    }
    private static Mesh strip(float[] heights, float red, float green, float blue) {
        int n = heights.length;
        float[] vertices = new float[n * 2 * 7];
        int p = 0;
        for (int i = 0; i < n; i++) {
            float z = (float) i / (n - 1);
            for (int side = 0; side < 2; side++) {
                vertices[p++] = side == 0 ? -1.0f : 1.0f;
                vertices[p++] = heights[i];
                vertices[p++] = z;
                vertices[p++] = red; vertices[p++] = green; vertices[p++] = blue; vertices[p++] = 1.0f;
            }
        }
        int[] indices = new int[(n - 1) * 6];
        int q = 0;
        for (int i = 0; i < n - 1; i++) {
            int a = i * 2, b = a + 1, c = a + 2, d = a + 3;
            indices[q++] = a; indices[q++] = b; indices[q++] = c;
            indices[q++] = c; indices[q++] = b; indices[q++] = d;
        }
        return new Mesh(vertices, indices);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private record Mesh(float[] vertices, int[] indices) {}
}
