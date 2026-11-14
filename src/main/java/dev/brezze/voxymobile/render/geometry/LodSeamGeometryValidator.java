package dev.brezze.voxymobile.render.geometry;

/** CPU-only validation gate for indexed XYZ+RGBA seam payloads before GPU upload. */
public final class LodSeamGeometryValidator {
    private LodSeamGeometryValidator() {}

    public record Report(boolean valid, String status, int vertexCount, int triangleCount,
                         int degenerateTriangles, long byteSize) {}

    public static Report validate(float[] vertices, int[] indices) {
        if (vertices == null || indices == null) return fail("null-payload", 0, 0, 0, 0);
        if (vertices.length % 7 != 0)
            return fail("vertex-layout", 0, 0, 0, 0);
        if (indices.length % 3 != 0) return fail("index-layout", vertices.length / 7, 0, 0, 0);
        int vertexCount = vertices.length / 7;
        int triangleCount = indices.length / 3;
        long bytes = (long) vertices.length * Float.BYTES + (long) indices.length * Integer.BYTES;
        if (vertexCount < 3 && triangleCount > 0)
            return fail("too-few-vertices", vertexCount, triangleCount, 0, bytes);
        for (int i = 0; i < vertices.length; i++) {
            if (!Float.isFinite(vertices[i])) return fail("non-finite-vertex", vertexCount, triangleCount, 0, bytes);
            int component = i % 7;
            if (component >= 3 && (vertices[i] < 0f || vertices[i] > 1f))
                return fail("rgba-out-of-range", vertexCount, triangleCount, 0, bytes);
        }
        for (int index : indices) {
            if (index < 0 || index >= vertexCount)
                return fail("index-out-of-range", vertexCount, triangleCount, 0, bytes);
        }
        int degenerate = 0;
        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i], b = indices[i + 1], c = indices[i + 2];
            if (a == b || b == c || a == c || areaSquared(vertices, a, b, c) <= 1.0e-20) degenerate++;
        }
        if (degenerate != 0) return fail("degenerate-triangle", vertexCount, triangleCount, degenerate, bytes);
        return new Report(true, "valid", vertexCount, triangleCount, 0, bytes);
    }


    private static double areaSquared(float[] v, int ai, int bi, int ci) {
        int a = ai * 7, b = bi * 7, c = ci * 7;
        double abx = v[b] - v[a], aby = v[b + 1] - v[a + 1], abz = v[b + 2] - v[a + 2];
        double acx = v[c] - v[a], acy = v[c + 1] - v[a + 1], acz = v[c + 2] - v[a + 2];
        double x = aby * acz - abz * acy, y = abz * acx - abx * acz, z = abx * acy - aby * acx;
        return x * x + y * y + z * z;
    }

    private static Report fail(String status, int vertices, int triangles, int degenerate, long bytes) {
        return new Report(false, status, vertices, triangles, degenerate, bytes);
    }
}
