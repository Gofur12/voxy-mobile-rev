package dev.brezze.voxymobile.render.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Extracts an ordered one-dimensional edge from XYZ+RGBA mesh vertices. CPU-only. */
public final class MeshBoundaryExtractor {
    private MeshBoundaryExtractor() {}

    /**
     * Selects vertices lying on a plane and a second fixed-coordinate line, then
     * returns unique XYZ points sorted along tangentAxis. Axis values are 0=X, 1=Y, 2=Z.
     * This intentionally extracts existing mesh vertices; it does not interpolate edges.
     */
    public static float[] extractEdge(float[] packedVertices, int normalAxis, float plane,
                                      int tangentAxis, int fixedAxis, float fixedValue,
                                      float tolerance) {
        if (packedVertices == null || packedVertices.length % LodSeamPatchData.FLOATS_PER_VERTEX != 0)
            throw new IllegalArgumentException("packedVertices must use XYZ+RGBA layout");
        if (normalAxis < 0 || normalAxis > 2 || tangentAxis < 0 || tangentAxis > 2 || fixedAxis < 0 || fixedAxis > 2)
            throw new IllegalArgumentException("axes must be 0, 1, or 2");
        if (normalAxis == tangentAxis || normalAxis == fixedAxis || tangentAxis == fixedAxis)
            throw new IllegalArgumentException("normal, tangent, and fixed axes must differ");
        if (!Float.isFinite(plane) || !Float.isFinite(fixedValue) || !Float.isFinite(tolerance) || tolerance < 0)
            throw new IllegalArgumentException("plane, fixedValue and tolerance must be finite; tolerance >= 0");

        List<float[]> points = new ArrayList<>();
        for (int i = 0; i < packedVertices.length; i += LodSeamPatchData.FLOATS_PER_VERTEX) {
            float x = packedVertices[i], y = packedVertices[i + 1], z = packedVertices[i + 2];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
                throw new IllegalArgumentException("mesh contains non-finite position");
            float[] p = {x, y, z};
            if (Math.abs(p[normalAxis] - plane) <= tolerance && Math.abs(p[fixedAxis] - fixedValue) <= tolerance)
                points.add(p);
        }
        points.sort(Comparator.comparingDouble(p -> p[tangentAxis]));
        List<float[]> unique = new ArrayList<>();
        for (float[] p : points) {
            if (unique.isEmpty() || Math.abs(unique.get(unique.size() - 1)[tangentAxis] - p[tangentAxis]) > tolerance)
                unique.add(p);
        }
        if (unique.size() < 2) return new float[0];
        float[] out = new float[unique.size() * 3];
        int offset = 0;
        for (float[] p : unique) { out[offset++] = p[0]; out[offset++] = p[1]; out[offset++] = p[2]; }
        return out;
    }
}
