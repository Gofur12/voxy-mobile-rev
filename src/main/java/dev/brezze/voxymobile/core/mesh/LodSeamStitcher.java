package dev.brezze.voxymobile.core.mesh;

import java.util.Arrays;

/** Builds zipper triangles between ordered terrain boundary polylines. */
public final class LodSeamStitcher {
    private LodSeamStitcher() {}

    /** Legacy equal-endpoint zipper retained for callers that share full XYZ endpoints. */
    public static int[] stitch(float[] fineEdge, float[] coarseEdge) {
        validate(fineEdge, "fineEdge");
        validate(coarseEdge, "coarseEdge");
        int fineCount = fineEdge.length / 3, coarseCount = coarseEdge.length / 3;
        if (!samePoint(fineEdge, 0, coarseEdge, 0)
                || !samePoint(fineEdge, fineCount - 1, coarseEdge, coarseCount - 1))
            throw new IllegalArgumentException("edge endpoints must match and use the same direction");
        return zipper(fineCount, coarseCount, i -> (double) i / (fineCount - 1),
                i -> (double) i / (coarseCount - 1));
    }

    /**
     * Terrain seam zipper. Boundary curves may have different heights at their ends;
     * only their shared horizontal progress coordinate must overlap and use the same direction.
     */
    public static int[] stitch(float[] fineEdge, float[] coarseEdge, int progressAxis, float tolerance) {
        validate(fineEdge, "fineEdge");
        validate(coarseEdge, "coarseEdge");
        if (progressAxis < 0 || progressAxis > 2 || !Float.isFinite(tolerance) || tolerance < 0)
            throw new IllegalArgumentException("invalid progress axis or tolerance");
        int fineCount = fineEdge.length / 3, coarseCount = coarseEdge.length / 3;
        double fs = coordinate(fineEdge, 0, progressAxis), fe = coordinate(fineEdge, fineCount - 1, progressAxis);
        double cs = coordinate(coarseEdge, 0, progressAxis), ce = coordinate(coarseEdge, coarseCount - 1, progressAxis);
        if (Math.abs(fs - cs) > tolerance || Math.abs(fe - ce) > tolerance)
            throw new IllegalArgumentException("projected edge endpoints must overlap and use the same direction");
        double fSpan = fe - fs, cSpan = ce - cs;
        if (Math.abs(fSpan) <= Math.max(tolerance, 1.0e-7) || Math.abs(cSpan) <= Math.max(tolerance, 1.0e-7))
            throw new IllegalArgumentException("projected edge span is zero");
        ensureMonotonic(fineEdge, fineCount, progressAxis, fSpan);
        ensureMonotonic(coarseEdge, coarseCount, progressAxis, cSpan);
        return zipper(fineCount, coarseCount,
                i -> (coordinate(fineEdge, i, progressAxis) - fs) / fSpan,
                i -> (coordinate(coarseEdge, i, progressAxis) - cs) / cSpan);
    }

    private interface Progress { double at(int index); }
    private static int[] zipper(int fineCount, int coarseCount, Progress fine, Progress coarse) {
        int[] indices = new int[(fineCount + coarseCount - 2) * 3];
        int out = 0, fi = 0, ci = 0;
        while (fi < fineCount - 1 || ci < coarseCount - 1) {
            double nf = fi < fineCount - 1 ? fine.at(fi + 1) : Double.POSITIVE_INFINITY;
            double nc = ci < coarseCount - 1 ? coarse.at(ci + 1) : Double.POSITIVE_INFINITY;
            int f = fi, c = fineCount + ci;
            if (nf <= nc) {
                indices[out++] = f; indices[out++] = c; indices[out++] = f + 1; fi++;
            } else {
                indices[out++] = f; indices[out++] = c; indices[out++] = c + 1; ci++;
            }
        }
        return out == indices.length ? indices : Arrays.copyOf(indices, out);
    }

    private static void ensureMonotonic(float[] edge, int count, int axis, double span) {
        double previous = coordinate(edge, 0, axis);
        for (int i = 1; i < count; i++) {
            double current = coordinate(edge, i, axis);
            if (span > 0 ? current + 1.0e-6 < previous : current - 1.0e-6 > previous)
                throw new IllegalArgumentException("projected boundary is not monotonic");
            previous = current;
        }
    }
    private static double coordinate(float[] edge, int point, int axis) { return edge[point * 3 + axis]; }
    private static void validate(float[] edge, String name) {
        if (edge == null || edge.length < 6 || edge.length % 3 != 0)
            throw new IllegalArgumentException(name + " must contain at least two XYZ points");
        for (float value : edge) if (!Float.isFinite(value))
            throw new IllegalArgumentException(name + " contains a non-finite coordinate");
    }
    private static boolean samePoint(float[] a, int ai, float[] b, int bi) {
        int ao = ai * 3, bo = bi * 3;
        return Math.abs(a[ao] - b[bo]) < 1.0e-4f
                && Math.abs(a[ao + 1] - b[bo + 1]) < 1.0e-4f
                && Math.abs(a[ao + 2] - b[bo + 2]) < 1.0e-4f;
    }
}
