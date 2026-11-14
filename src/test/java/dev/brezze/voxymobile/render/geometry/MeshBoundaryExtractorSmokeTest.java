package dev.brezze.voxymobile.render.geometry;

public final class MeshBoundaryExtractorSmokeTest {
    public static void main(String[] args) {
        // Two triangles contribute duplicate vertices on the same X/Z boundary line.
        float[] mesh = {
            4,2,9, 1,1,1,1, 4,0,9, 1,1,1,1, 4,1,9, 1,1,1,1,
            4,1,9, 1,1,1,1, 4,3,9, 1,1,1,1, 4,2,9, 1,1,1,1
        };
        float[] edge = MeshBoundaryExtractor.extractEdge(mesh, 0, 4, 1, 2, 9, 0.0001f);
        check(edge.length == 12, "four unique XYZ points");
        check(edge[1] == 0 && edge[4] == 1 && edge[7] == 2 && edge[10] == 3, "sorted along tangent axis");
        check(MeshBoundaryExtractor.extractEdge(mesh, 0, 8, 1, 2, 9, 0.001f).length == 0, "empty unmatched plane");
        expectFailure(() -> MeshBoundaryExtractor.extractEdge(mesh, 0, Float.NaN, 1, 2, 9, 0.01f));
        expectFailure(() -> MeshBoundaryExtractor.extractEdge(mesh, 0, 4, 0, 2, 9, 0.01f));
        expectFailure(() -> MeshBoundaryExtractor.extractEdge(new float[]{1,2,3}, 0, 1, 1, 2, 3, 0.1f));
        System.out.println("MeshBoundaryExtractorSmokeTest PASS (6 assertions)");
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static void expectFailure(Runnable r) {
        try { r.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
