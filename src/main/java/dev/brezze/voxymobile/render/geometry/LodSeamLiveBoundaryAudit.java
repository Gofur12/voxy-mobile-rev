package dev.brezze.voxymobile.render.geometry;

/**
 * Captures boundary traces from the exact indexed terrain mesh snapshots used by the
 * live seam producer. CPU-only: safe to run on the seam worker before triangulation.
 */
public final class LodSeamLiveBoundaryAudit {
    private LodSeamLiveBoundaryAudit() {}

    public record SideSnapshot(int segmentCount, int vertexCount, long byteSize,
                               int contourCount, int openContourCount, int closedContourCount) {
        public boolean isEmpty() { return segmentCount == 0 || contourCount == 0; }
    }

    public record Snapshot(SideSnapshot fine, SideSnapshot coarse) {
        public boolean bothSidesPresent() { return !fine.isEmpty() && !coarse.isEmpty(); }
        public int totalSegments() { return fine.segmentCount() + coarse.segmentCount(); }
        public long totalBytes() { return fine.byteSize() + coarse.byteSize(); }
    }

    public record Capture(Snapshot summary, MeshPlaneBoundaryExtractor.BoundarySegments fine,
                          MeshPlaneBoundaryExtractor.BoundarySegments coarse) {}

    /** Extracts both active mesh boundaries and records a compact diagnostic summary. */
    public static Capture capture(float[] fineVertices, int[] fineIndices,
                                  float[] coarseVertices, int[] coarseIndices,
                                  int planeAxis, float plane,
                                  int rangeAxisA, float minA, float maxA,
                                  int rangeAxisB, float minB, float maxB,
                                  float tolerance, int maxSegments, int maxContours) {
        if (maxContours < 1) throw new IllegalArgumentException("maxContours must be positive");
        MeshPlaneBoundaryExtractor.BoundarySegments fine = MeshPlaneBoundaryExtractor.extract(
                fineVertices, fineIndices, planeAxis, plane, rangeAxisA, minA, maxA,
                rangeAxisB, minB, maxB, tolerance, maxSegments);
        MeshPlaneBoundaryExtractor.BoundarySegments coarse = MeshPlaneBoundaryExtractor.extract(
                coarseVertices, coarseIndices, planeAxis, plane, rangeAxisA, minA, maxA,
                rangeAxisB, minB, maxB, tolerance, maxSegments);
        SideSnapshot fineSummary = summarize(fine, tolerance, maxContours);
        SideSnapshot coarseSummary = summarize(coarse, tolerance, maxContours);
        return new Capture(new Snapshot(fineSummary, coarseSummary), fine, coarse);
    }

    private static SideSnapshot summarize(MeshPlaneBoundaryExtractor.BoundarySegments segments,
                                          float tolerance, int maxContours) {
        BoundaryContourStitcher.Contours contours = BoundaryContourStitcher.stitch(
                segments.copyVertices(), segments.segmentCount(), Math.max(tolerance, 1.0e-6f), maxContours);
        int open = 0, closed = 0;
        for (int i = 0; i < contours.contourCount(); i++) {
            if (contours.isClosed(i)) closed++; else open++;
        }
        return new SideSnapshot(segments.segmentCount(), segments.vertexCount(), segments.byteSize(),
                contours.contourCount(), open, closed);
    }
}
