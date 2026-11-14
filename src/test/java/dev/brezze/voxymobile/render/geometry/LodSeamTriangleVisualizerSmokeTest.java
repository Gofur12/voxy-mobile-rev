package dev.brezze.voxymobile.render.geometry;

import java.util.List;

public final class LodSeamTriangleVisualizerSmokeTest {
    public static void main(String[] args) {
        float[] vertices = {
                10,20,30, 1,0,0,1,
                12,20,30, 0,1,0,1,
                10,22,30, 0,0,1,1
        };
        float[] lines = LodSeamTriangleVisualizer.build(vertices, new int[]{0,1,2}, 10,20,30);
        check(lines.length == 3 * 2 * 7, "one triangle becomes three line edges");
        check(lines[0] == 0 && lines[1] == 0 && lines[2] == 0, "camera-relative first vertex");
        check(lines[3] == 0.1f && lines[4] == 0.9f && lines[5] == 1f && lines[6] == 1f, "cyan debug color");
        check(lines[7] == 2 && lines[8] == 0 && lines[9] == 0, "first edge endpoint");
        float[] combined = LodSeamTriangleVisualizer.combine(List.of(lines, lines));
        check(combined.length == lines.length * 2, "mesh payloads combine");
        check(LodSeamTriangleVisualizer.combine(List.of()).length == 0, "empty collection");
        boolean rejected = false;
        try { LodSeamTriangleVisualizer.build(vertices, new int[]{0,1,3}, 0,0,0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "out-of-range index rejected");
        System.out.println("LodSeamTriangleVisualizerSmokeTest: PASS (7 assertions)");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
