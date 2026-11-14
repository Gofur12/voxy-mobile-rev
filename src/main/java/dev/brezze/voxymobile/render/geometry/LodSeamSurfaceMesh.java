package dev.brezze.voxymobile.render.geometry;

/** Combines a stitched closed contour and CPU triangulation into an XYZ+RGBA indexed mesh. */
public final class LodSeamSurfaceMesh {
    private final float[] vertices;
    private final int[] indices;
    private final float[] normal;

    private LodSeamSurfaceMesh(float[] vertices, int[] indices, float[] normal) {
        this.vertices = vertices;
        this.indices = indices;
        this.normal = normal;
    }

    /**
     * Builds a CPU mesh from a packed contour (7 floats per point: XYZ+RGBA).
     * A repeated final copy of the first point is removed before packing. The
     * triangulator only accepts simple, planar, closed contours.
     */
    public static LodSeamSurfaceMesh build(float[] contour, boolean closed, float tolerance) {
        ContourSurfaceTriangulator.Result triangulation =
                ContourSurfaceTriangulator.triangulate(contour, closed, tolerance);
        int count = contour.length / LodSeamPatchData.FLOATS_PER_VERTEX;
        if (count > 3 && distanceSquared(contour, 0, contour, (count - 1) * 7) <= tolerance * tolerance) {
            count--;
        }
        float[] packed = new float[count * LodSeamPatchData.FLOATS_PER_VERTEX];
        System.arraycopy(contour, 0, packed, 0, packed.length);
        int[] indices = triangulation.copyIndices();
        for (int index : indices) {
            if (index < 0 || index >= count) throw new IllegalStateException("triangulator returned out-of-range index");
        }
        return new LodSeamSurfaceMesh(packed, indices, triangulation.copyNormal());
    }

    private static double distanceSquared(float[] a, int ao, float[] b, int bo) {
        double x = a[ao] - b[bo], y = a[ao + 1] - b[bo + 1], z = a[ao + 2] - b[bo + 2];
        return x * x + y * y + z * z;
    }

    public int vertexCount() { return vertices.length / LodSeamPatchData.FLOATS_PER_VERTEX; }
    public int indexCount() { return indices.length; }
    public int triangleCount() { return indices.length / 3; }
    public long byteSize() { return (long) vertices.length * Float.BYTES + (long) indices.length * Integer.BYTES; }
    public float[] copyVertices() { return vertices.clone(); }
    public int[] copyIndices() { return indices.clone(); }
    public float[] copyNormal() { return normal.clone(); }
}
