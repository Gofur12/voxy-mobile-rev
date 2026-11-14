package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.mesh.LodSeamStitcher;

/** Immutable GLES-ready seam payload: XYZ+RGBA vertices and zipper indices. */
public final class LodSeamPatchData {
    public static final int FLOATS_PER_VERTEX = 7;
    private final float[] vertices;
    private final int[] indices;

    private LodSeamPatchData(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    /**
     * Packs two ordered XYZ boundary edges into the renderer's seven-float vertex
     * layout and builds indices for their zipper patch. Both edges must use the
     * same direction and share endpoints; coordinates must already be in the
     * same world-space coordinate system as the terrain meshes.
     */
    public static LodSeamPatchData build(float[] fineEdge, float[] coarseEdge,
                                         float red, float green, float blue, float alpha) {
        validateColor(red, "red"); validateColor(green, "green");
        validateColor(blue, "blue"); validateColor(alpha, "alpha");
        int[] seamIndices = LodSeamStitcher.stitch(fineEdge, coarseEdge);
        int pointCount = fineEdge.length / 3 + coarseEdge.length / 3;
        float[] packed = new float[pointCount * FLOATS_PER_VERTEX];
        int out = 0;
        out = appendEdge(packed, out, fineEdge, red, green, blue, alpha);
        appendEdge(packed, out, coarseEdge, red, green, blue, alpha);
        return new LodSeamPatchData(packed, seamIndices);
    }


    /** Builds a zipper patch while preserving interpolated RGBA values from both source meshes. */
    public static LodSeamPatchData buildPackedEdges(float[] fineEdge, float[] coarseEdge) {
        validatePackedEdge(fineEdge, "fineEdge");
        validatePackedEdge(coarseEdge, "coarseEdge");
        float[] fineXYZ = xyz(fineEdge);
        float[] coarseXYZ = xyz(coarseEdge);
        int[] raw = LodSeamStitcher.stitch(fineXYZ, coarseXYZ);
        float[] packed = new float[fineEdge.length + coarseEdge.length];
        System.arraycopy(fineEdge, 0, packed, 0, fineEdge.length);
        System.arraycopy(coarseEdge, 0, packed, fineEdge.length, coarseEdge.length);
        int[] clean = orientAndFilter(packed, raw);
        if (clean.length == 0) throw new IllegalArgumentException("seam contains no non-degenerate triangles");
        return new LodSeamPatchData(packed, clean);
    }

    private static void validatePackedEdge(float[] edge, String name) {
        if (edge == null || edge.length < 14 || edge.length % FLOATS_PER_VERTEX != 0)
            throw new IllegalArgumentException(name + " must contain at least two packed XYZ+RGBA points");
        for (float value : edge) if (!Float.isFinite(value))
            throw new IllegalArgumentException(name + " contains non-finite vertex data");
        for (int i = 3; i < edge.length; i += FLOATS_PER_VERTEX) {
            for (int c = 0; c < 4; c++) if (edge[i + c] < 0.0f || edge[i + c] > 1.0f)
                throw new IllegalArgumentException(name + " color channels must be within 0..1");
        }
    }

    private static float[] xyz(float[] packed) {
        float[] result = new float[(packed.length / FLOATS_PER_VERTEX) * 3];
        for (int i = 0, o = 0; i < packed.length; i += FLOATS_PER_VERTEX) {
            result[o++] = packed[i]; result[o++] = packed[i + 1]; result[o++] = packed[i + 2];
        }
        return result;
    }

    /** Drops zero-area zipper triangles and makes all remaining faces share one winding. */
    private static int[] orientAndFilter(float[] vertices, int[] indices) {
        java.util.ArrayList<Integer> output = new java.util.ArrayList<>(indices.length);
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i], b = indices[i + 1], c = indices[i + 2];
            int ao = a * FLOATS_PER_VERTEX, bo = b * FLOATS_PER_VERTEX, co = c * FLOATS_PER_VERTEX;
            double abx = vertices[bo] - vertices[ao], aby = vertices[bo + 1] - vertices[ao + 1], abz = vertices[bo + 2] - vertices[ao + 2];
            double acx = vertices[co] - vertices[ao], acy = vertices[co + 1] - vertices[ao + 1], acz = vertices[co + 2] - vertices[ao + 2];
            double tx = aby * acz - abz * acy, ty = abz * acx - abx * acz, tz = abx * acy - aby * acx;
            double area2 = tx * tx + ty * ty + tz * tz;
            if (!Double.isFinite(area2) || area2 <= 1.0e-12) continue;
            if (nx == 0 && ny == 0 && nz == 0) { nx = tx; ny = ty; nz = tz; }
            else if (tx * nx + ty * ny + tz * nz < 0) { int swap = b; b = c; c = swap; }
            output.add(a); output.add(b); output.add(c);
        }
        int[] result = new int[output.size()];
        for (int i = 0; i < result.length; i++) result[i] = output.get(i);
        return result;
    }

    private static int appendEdge(float[] target, int offset, float[] edge,
                                  float red, float green, float blue, float alpha) {
        for (int i = 0; i < edge.length; i += 3) {
            target[offset++] = edge[i];
            target[offset++] = edge[i + 1];
            target[offset++] = edge[i + 2];
            target[offset++] = red;
            target[offset++] = green;
            target[offset++] = blue;
            target[offset++] = alpha;
        }
        return offset;
    }

    private static void validateColor(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f)
            throw new IllegalArgumentException(name + " must be finite and within 0..1");
    }

    public int vertexCount() { return vertices.length / FLOATS_PER_VERTEX; }
    public int indexCount() { return indices.length; }
    public int triangleCount() { return indices.length / 3; }
    public long byteSize() { return (long) vertices.length * Float.BYTES + (long) indices.length * Integer.BYTES; }
    public float[] copyVertices() { return vertices.clone(); }
    public int[] copyIndices() { return indices.clone(); }
}
