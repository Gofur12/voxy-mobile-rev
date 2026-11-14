package dev.brezze.voxymobile.render.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts triangle/plane intersection segments from the live XYZ+RGBA mesh payload.
 * CPU-only and bounded; it does not create seam triangles or touch GLES state.
 */
public final class MeshPlaneBoundaryExtractor {
    private MeshPlaneBoundaryExtractor() {}

    public static final class BoundarySegments {
        private final float[] vertices;
        private final int segmentCount;
        private BoundarySegments(float[] vertices, int segmentCount) {
            this.vertices = vertices;
            this.segmentCount = segmentCount;
        }
        /** Two XYZ+RGBA vertices per segment. */
        public float[] copyVertices() { return vertices.clone(); }
        public int segmentCount() { return segmentCount; }
        public int vertexCount() { return vertices.length / LodSeamPatchData.FLOATS_PER_VERTEX; }
        public long byteSize() { return (long) vertices.length * Float.BYTES; }
        public boolean isEmpty() { return segmentCount == 0; }
    }

    /**
     * Intersects indexed triangles with an axis-aligned plane and retains segments
     * whose endpoints lie inside the supplied overlap rectangle on the other axes.
     * Axes are X=0, Y=1, Z=2. Fully coplanar triangles are ignored to avoid
     * treating an entire surface as a boundary trace.
     */
    public static BoundarySegments extract(float[] packed, int[] indices,
                                            int planeAxis, float plane,
                                            int rangeAxisA, float minA, float maxA,
                                            int rangeAxisB, float minB, float maxB,
                                            float tolerance, int maxSegments) {
        if (packed == null || packed.length % LodSeamPatchData.FLOATS_PER_VERTEX != 0)
            throw new IllegalArgumentException("packed must use XYZ+RGBA layout");
        if (indices == null || indices.length % 3 != 0)
            throw new IllegalArgumentException("indices must contain triangles");
        if (planeAxis < 0 || planeAxis > 2 || rangeAxisA < 0 || rangeAxisA > 2 || rangeAxisB < 0 || rangeAxisB > 2
                || planeAxis == rangeAxisA || planeAxis == rangeAxisB || rangeAxisA == rangeAxisB)
            throw new IllegalArgumentException("axes must be distinct values in 0..2");
        if (!Float.isFinite(plane) || !Float.isFinite(minA) || !Float.isFinite(maxA)
                || !Float.isFinite(minB) || !Float.isFinite(maxB) || minA > maxA || minB > maxB
                || !Float.isFinite(tolerance) || tolerance < 0 || maxSegments < 1)
            throw new IllegalArgumentException("invalid bounds, tolerance, or segment limit");
        int vertexCount = packed.length / LodSeamPatchData.FLOATS_PER_VERTEX;
        for (int index : indices) if (index < 0 || index >= vertexCount)
            throw new IllegalArgumentException("index outside packed vertex array");

        List<Float> out = new ArrayList<>();
        int segments = 0;
        for (int t = 0; t < indices.length && segments < maxSegments; t += 3) {
            float[][] tri = {vertex(packed, indices[t]), vertex(packed, indices[t + 1]), vertex(packed, indices[t + 2])};
            int coplanar = 0;
            for (float[] v : tri) if (Math.abs(v[planeAxis] - plane) <= tolerance) coplanar++;
            if (coplanar == 3) continue;
            List<float[]> hits = new ArrayList<>(3);
            for (int e = 0; e < 3; e++) {
                float[] a = tri[e], b = tri[(e + 1) % 3];
                float da = a[planeAxis] - plane, db = b[planeAxis] - plane;
                boolean onA = Math.abs(da) <= tolerance, onB = Math.abs(db) <= tolerance;
                if (onA) addUnique(hits, withPlane(a, plane, planeAxis), tolerance);
                if (!onA && !onB && ((da < 0 && db > 0) || (da > 0 && db < 0))) {
                    float f = da / (da - db);
                    addUnique(hits, interpolate(a, b, f, plane, planeAxis), tolerance);
                }
            }
            if (hits.size() < 2) continue;
            float[] p = hits.get(0), q = hits.get(1);
            float farthest = distanceSquared(p, q);
            for (int i = 0; i < hits.size(); i++) for (int j = i + 1; j < hits.size(); j++) {
                float d = distanceSquared(hits.get(i), hits.get(j));
                if (d > farthest) { farthest = d; p = hits.get(i); q = hits.get(j); }
            }
            if (farthest <= tolerance * tolerance || !inside(p, rangeAxisA, minA, maxA, rangeAxisB, minB, maxB, tolerance)
                    || !inside(q, rangeAxisA, minA, maxA, rangeAxisB, minB, maxB, tolerance)) continue;
            append(out, p); append(out, q); segments++;
        }
        float[] result = new float[out.size()];
        for (int i = 0; i < out.size(); i++) result[i] = out.get(i);
        return new BoundarySegments(result, segments);
    }

    private static float[] vertex(float[] packed, int index) {
        int o = index * LodSeamPatchData.FLOATS_PER_VERTEX;
        float[] v = new float[7];
        System.arraycopy(packed, o, v, 0, 7);
        for (float value : v) if (!Float.isFinite(value)) throw new IllegalArgumentException("mesh contains non-finite vertex data");
        return v;
    }
    private static float[] withPlane(float[] v, float plane, int axis) { float[] r = v.clone(); r[axis] = plane; return r; }
    private static float[] interpolate(float[] a, float[] b, float f, float plane, int axis) {
        float[] r = new float[7];
        for (int i = 0; i < 7; i++) r[i] = a[i] + (b[i] - a[i]) * f;
        r[axis] = plane; return r;
    }
    private static void addUnique(List<float[]> hits, float[] p, float eps) {
        for (float[] q : hits) if (distanceSquared(p, q) <= eps * eps) return;
        hits.add(p);
    }
    private static float distanceSquared(float[] a, float[] b) {
        float x = a[0]-b[0], y = a[1]-b[1], z = a[2]-b[2]; return x*x+y*y+z*z;
    }
    private static boolean inside(float[] p, int a, float minA, float maxA, int b, float minB, float maxB, float eps) {
        return p[a] >= minA-eps && p[a] <= maxA+eps && p[b] >= minB-eps && p[b] <= maxB+eps;
    }
    private static void append(List<Float> out, float[] v) { for (int i = 0; i < 7; i++) out.add(v[i]); }
}
