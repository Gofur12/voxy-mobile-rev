package dev.brezze.voxymobile.render.geometry;

public final class LodSeamLiveBoundaryAuditSmokeTest {
    public static void main(String[] args) {
        // Each triangle crosses X=0 and contributes one Y/Z boundary segment.
        float[] mesh = {
                -1,0,0, 1,1,1, 1,  1,0,0, 1,1,1,1,  1,2,0, 1,1,1,1
        };
        int[] indices = {0,1,2};
        var capture = LodSeamLiveBoundaryAudit.capture(mesh, indices, mesh, indices,
                0, 0, 1, -1, 3, 2, -1, 1, 1.0e-4f, 32, 16);
        var summary = capture.summary();
        check(summary.bothSidesPresent(), "both live mesh sides extracted");
        check(summary.fine().segmentCount() == 1 && summary.coarse().segmentCount() == 1,
                "one segment extracted per side");
        check(summary.fine().vertexCount() == 2, "segment stores two vertices");
        check(summary.fine().contourCount() == 1 && summary.fine().openContourCount() == 1,
                "open contour summary recorded");
        check(summary.totalSegments() == 2, "combined segment count");
        check(summary.totalBytes() > 0, "boundary memory estimate available");
        check(capture.fine().copyVertices().length == 14, "fine snapshot is packed XYZ+RGBA");
        boolean invalidLimit = false;
        try {
            LodSeamLiveBoundaryAudit.capture(mesh, indices, mesh, indices,
                    0, 0, 1, -1, 3, 2, -1, 1, 1.0e-4f, 32, 0);
        } catch (IllegalArgumentException expected) { invalidLimit = true; }
        check(invalidLimit, "invalid contour cap rejected");
        System.out.println("LodSeamLiveBoundaryAuditSmokeTest: PASS (8 assertions)");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
