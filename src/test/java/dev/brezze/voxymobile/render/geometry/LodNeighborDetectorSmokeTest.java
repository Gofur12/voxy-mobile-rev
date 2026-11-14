package dev.brezze.voxymobile.render.geometry;

public final class LodNeighborDetectorSmokeTest {
    public static void main(String[] args) {
        LodNeighborDetector.Region west = new LodNeighborDetector.Region(-16, 0, 0, 16, 0);
        LodNeighborDetector.Region east = new LodNeighborDetector.Region(0, 4, 32, 12, 2);
        LodNeighborDetector.NeighborPair pair = LodNeighborDetector.detect(west, east);
        check(pair != null, "detects east-west adjacency");
        check(pair.edge == LodNeighborDetector.Edge.EAST_WEST, "edge orientation");
        check(pair.plane == 0.0, "shared plane");
        check(pair.overlapMin == 4.0 && pair.overlapMax == 12.0, "partial overlap range");
        check(pair.lodDelta == 2 && pair.requiresSeam(), "detects LOD mismatch");
        check(LodNeighborDetector.detect(east, west).edge == LodNeighborDetector.Edge.WEST_EAST, "reverse orientation");
        LodNeighborDetector.Region north = new LodNeighborDetector.Region(0, -16, 16, 0, 1);
        LodNeighborDetector.Region south = new LodNeighborDetector.Region(2, 0, 14, 16, 1);
        check(LodNeighborDetector.detect(north, south).edge == LodNeighborDetector.Edge.SOUTH_NORTH, "north-south adjacency");
        check(!LodNeighborDetector.detect(north, south).requiresSeam(), "equal LOD needs no seam");
        check(LodNeighborDetector.detect(west, new LodNeighborDetector.Region(1, 0, 17, 16, 1)) == null, "gap is not adjacency");
        check(LodNeighborDetector.detect(west, new LodNeighborDetector.Region(0, 20, 16, 30, 1)) == null, "corner-only touch is not adjacency");
        expectFailure(() -> new LodNeighborDetector.Region(0, 0, 0, 16, 0));
        expectFailure(() -> LodNeighborDetector.detect(null, east));
        System.out.println("LodNeighborDetectorSmokeTest PASS (12 assertions)");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
