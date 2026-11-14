package dev.brezze.voxymobile.core.mesh;

import java.util.Arrays;

/**
 * Builds a zipper triangulation between two ordered terrain boundary polylines.
 * Each point is XYZ in the same local/world coordinate space. The returned
 * indices address the concatenation of fineEdge followed by coarseEdge.
 * This is a CPU geometry primitive; callers must supply matching endpoints and
 * integrate the patch into their mesh upload path.
 */
public final class LodSeamStitcher {
    private LodSeamStitcher() {}

    public static int[] stitch(float[] fineEdge, float[] coarseEdge) {
        validate(fineEdge, "fineEdge");
        validate(coarseEdge, "coarseEdge");
        int fineCount = fineEdge.length / 3;
        int coarseCount = coarseEdge.length / 3;
        if (!samePoint(fineEdge, 0, coarseEdge, 0) ||
            !samePoint(fineEdge, fineCount - 1, coarseEdge, coarseCount - 1)) {
            throw new IllegalArgumentException("edge endpoints must match and use the same direction");
        }

        int[] indices = new int[(fineCount + coarseCount - 2) * 3];
        int out = 0, fi = 0, ci = 0;
        while (fi < fineCount - 1 || ci < coarseCount - 1) {
            double nextFine = fi < fineCount - 1 ? (double) (fi + 1) / (fineCount - 1) : Double.POSITIVE_INFINITY;
            double nextCoarse = ci < coarseCount - 1 ? (double) (ci + 1) / (coarseCount - 1) : Double.POSITIVE_INFINITY;
            int f = fi;
            int c = fineCount + ci;
            if (nextFine <= nextCoarse) {
                indices[out++] = f;
                indices[out++] = c;
                indices[out++] = f + 1;
                fi++;
            } else {
                indices[out++] = f;
                indices[out++] = c;
                indices[out++] = c + 1;
                ci++;
            }
        }
        return out == indices.length ? indices : Arrays.copyOf(indices, out);
    }

    private static void validate(float[] edge, String name) {
        if (edge == null || edge.length < 6 || edge.length % 3 != 0)
            throw new IllegalArgumentException(name + " must contain at least two XYZ points");
        for (float value : edge) if (!Float.isFinite(value))
            throw new IllegalArgumentException(name + " contains a non-finite coordinate");
    }

    private static boolean samePoint(float[] a, int ai, float[] b, int bi) {
        int ao = ai * 3, bo = bi * 3;
        return Math.abs(a[ao] - b[bo]) < 1.0e-4f &&
               Math.abs(a[ao + 1] - b[bo + 1]) < 1.0e-4f &&
               Math.abs(a[ao + 2] - b[bo + 2]) < 1.0e-4f;
    }
}
