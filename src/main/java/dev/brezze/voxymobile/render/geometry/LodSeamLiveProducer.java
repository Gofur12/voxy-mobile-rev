package dev.brezze.voxymobile.render.geometry;

/**
 * Builds a seam payload directly from two selected terrain meshes. The producer is CPU-only;
 * the caller owns scheduling and hands the returned payload to the render-thread upload queue.
 */
public final class LodSeamLiveProducer {
    private LodSeamLiveProducer() {}

    public static final class Result {
        private final LodSeamGpuMeshData payload;
        private final String status;
        private final LodSeamLiveBoundaryAudit.Snapshot boundaryAudit;
        private Result(LodSeamGpuMeshData payload, String status, LodSeamLiveBoundaryAudit.Snapshot boundaryAudit) {
            this.payload = payload; this.status = status; this.boundaryAudit = boundaryAudit;
        }
        public LodSeamGpuMeshData payload() { return payload; }
        public String status() { return status; }
        public boolean generated() { return payload != null; }
        /** Boundary counts captured from the same active terrain mesh snapshots. */
        public LodSeamLiveBoundaryAudit.Snapshot boundaryAudit() { return boundaryAudit; }
    }

    public static Result generate(LodSeamKey key, long revision,
                                  float[] fineVertices, int[] fineIndices,
                                  float[] coarseVertices, int[] coarseIndices,
                                  int planeAxis, float plane,
                                  int rangeAxisA, float minA, float maxA,
                                  int rangeAxisB, float minB, float maxB,
                                  float tolerance, int maxSegments) {
        if (key == null || fineVertices == null || fineIndices == null || coarseVertices == null || coarseIndices == null)
            throw new IllegalArgumentException("key and both indexed meshes are required");
        LodSeamLiveBoundaryAudit.Capture capture = LodSeamLiveBoundaryAudit.capture(
                fineVertices, fineIndices, coarseVertices, coarseIndices,
                planeAxis, plane, rangeAxisA, minA, maxA, rangeAxisB, minB, maxB,
                tolerance, maxSegments, 64);
        MeshPlaneBoundaryExtractor.BoundarySegments fine = capture.fine();
        MeshPlaneBoundaryExtractor.BoundarySegments coarse = capture.coarse();
        LodSeamLiveBoundaryAudit.Snapshot audit = capture.summary();
        if (!audit.bothSidesPresent()) return new Result(null, "boundary-missing", audit);
        BoundaryContourStitcher.Contours fineContours = BoundaryContourStitcher.stitch(
                fine.copyVertices(), fine.segmentCount(), tolerance, 64);
        BoundaryContourStitcher.Contours coarseContours = BoundaryContourStitcher.stitch(
                coarse.copyVertices(), coarse.segmentCount(), tolerance, 64);
        EdgePair pair = matchingOpenPair(fineContours, coarseContours, rangeAxisB, tolerance);
        if (pair == null) return new Result(null, "matching-open-boundary-not-found", audit);
        float[] fineEdge = pair.fine;
        float[] coarseEdge = pair.coarse;
        try {
            LodSeamPatchData patch = LodSeamPatchData.buildPackedEdges(fineEdge, coarseEdge, rangeAxisB, tolerance);
            if (patch.indexCount() == 0) return new Result(null, "empty-patch", audit);
            return new Result(new LodSeamGpuMeshData(key, revision, patch), "generated", audit);
        } catch (IllegalArgumentException invalidGeometry) {
            return new Result(null, "invalid-boundary:" + invalidGeometry.getMessage(), audit);
        }
    }

    private static EdgePair matchingOpenPair(BoundaryContourStitcher.Contours fine,
                                             BoundaryContourStitcher.Contours coarse,
                                             int progressAxis, float tolerance) {
        EdgePair best = null;
        int bestScore = -1;
        for (int i = 0; i < fine.contourCount(); i++) {
            if (fine.isClosed(i)) continue;
            float[] f = fine.copyContour(i);
            for (int j = 0; j < coarse.contourCount(); j++) {
                if (coarse.isClosed(j)) continue;
                float[] c = coarse.copyContour(j);
                if (!projectedEndpointsMatch(f, c, progressAxis, tolerance)) reversePacked(c);
                if (!projectedEndpointsMatch(f, c, progressAxis, tolerance)) continue;
                int score = f.length / 7 + c.length / 7;
                if (score > bestScore) { best = new EdgePair(f, c); bestScore = score; }
            }
        }
        return best;
    }
    private record EdgePair(float[] fine, float[] coarse) {}
    private static boolean projectedEndpointsMatch(float[] a, float[] b, int axis, float eps) {
        int an = a.length - 7, bn = b.length - 7;
        return Math.abs(a[axis] - b[axis]) <= eps
                && Math.abs(a[an + axis] - b[bn + axis]) <= eps;
    }
    private static void reversePacked(float[] points) {
        for (int i = 0, j = points.length - 7; i < j; i += 7, j -= 7) {
            for (int k = 0; k < 7; k++) { float v = points[i + k]; points[i + k] = points[j + k]; points[j + k] = v; }
        }
    }
}
