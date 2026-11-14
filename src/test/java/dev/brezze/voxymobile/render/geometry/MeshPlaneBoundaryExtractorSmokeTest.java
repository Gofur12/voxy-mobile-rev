package dev.brezze.voxymobile.render.geometry;

public final class MeshPlaneBoundaryExtractorSmokeTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void expect(Runnable r) { assertions++; try { r.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("expected IllegalArgumentException"); }
    public static void main(String[] args) {
        float[] v = {
            -1,0,0, 1,0,0,1,  1,0,0, 1,0,0,1,  1,2,0, 1,0,0,1,
            -1,0,0, 1,0,0,1,  1,2,0, 1,0,0,1,  -1,2,0, 1,0,0,1
        };
        int[] ix = {0,1,2, 3,4,5};
        var result = MeshPlaneBoundaryExtractor.extract(v, ix, 0, 0, 1, 0, 2, 2, -1, 1, 1e-5f, 8);
        check(result.segmentCount() == 2, "two triangles intersect plane");
        check(result.vertexCount() == 4, "two endpoints per segment");
        float[] data = result.copyVertices();
        check(data.length == 28, "packed seven-float layout");
        check(Math.abs(data[0]) < 1e-6 && Math.abs(data[7]) < 1e-6, "intersection x lies on plane");
        check(Math.abs(data[1]) < 1e-6 && Math.abs(data[8]-1) < 1e-6, "intersection endpoints retained");
        check(result.byteSize() == 28L * Float.BYTES, "byte accounting");
        data[0] = 99;
        check(Math.abs(result.copyVertices()[0]) < 1e-6, "defensive copy");
        var limited = MeshPlaneBoundaryExtractor.extract(v, ix, 0, 0, 1, 0, 2, 2, -1, 1, 1e-5f, 1);
        check(limited.segmentCount() == 1, "segment cap");
        var empty = MeshPlaneBoundaryExtractor.extract(v, ix, 0, 5, 1, 0, 2, 2, -1, 1, 1e-5f, 8);
        check(empty.isEmpty(), "no plane intersection");
        expect(() -> MeshPlaneBoundaryExtractor.extract(v, ix, 0, 0, 0, 0, 2, 2, -1, 1, 1e-5f, 8));
        expect(() -> MeshPlaneBoundaryExtractor.extract(v, new int[]{0,1,99}, 0, 0, 1, 0, 2, 2, -1, 1, 1e-5f, 8));
        expect(() -> MeshPlaneBoundaryExtractor.extract(v, ix, 0, 0, 1, 2, 1, 2, -1, 1, 1e-5f, 8));
        expect(() -> MeshPlaneBoundaryExtractor.extract(v, ix, 0, 0, 1, 0, 2, 2, -1, 1, -1, 8));
        System.out.println("MeshPlaneBoundaryExtractorSmokeTest PASS (12 assertions)");
    }
}
