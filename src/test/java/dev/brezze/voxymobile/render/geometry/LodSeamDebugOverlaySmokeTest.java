package dev.brezze.voxymobile.render.geometry;

import java.util.List;

public final class LodSeamDebugOverlaySmokeTest {
    public static void main(String[] args) {
        check(LodSeamDebugOverlay.build(List.of(),0,0,0).length == 0, "empty bounds");
        LodSeamBounds b = new LodSeamBounds(10,20,30,12,24,36);
        float[] lines = LodSeamDebugOverlay.build(List.of(b), 10,20,30);
        check(lines.length == 12 * 2 * 7, "12 AABB edges");
        check(lines[0] == 0 && lines[1] == 0 && lines[2] == 0, "camera relative minimum");
        check(lines[3] == 1 && lines[4] == 0.35f && lines[5] == 0.05f && lines[6] == 1, "debug color");
        check(lines[7] == 2 && lines[8] == 0 && lines[9] == 0, "first edge endpoint");
        float[] shifted = LodSeamDebugOverlay.build(List.of(b), 9,19,29);
        check(shifted[0] == 1 && shifted[1] == 1 && shifted[2] == 1, "camera translation");
        System.out.println("LodSeamDebugOverlaySmokeTest: PASS (6 assertions)");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
