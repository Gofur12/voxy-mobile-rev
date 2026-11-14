package dev.brezze.voxymobile.render.geometry;

import java.util.List;

public final class LodSeamFailureOverlaySmokeTest {
    private static int assertions;
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        LodSeamKey key = new LodSeamKey(10L, 20L);
        LodSeamBounds bounds = new LodSeamBounds(10, 20, 30, 11, 21, 31);
        LodSeamFailureMarker marker = new LodSeamFailureMarker(key, 7, bounds, "boundary-missing");
        float[] packed = LodSeamFailureOverlay.build(List.of(marker), 1, 2, 3);
        check(packed.length == 12 * 2 * 7, "12 box edges with two endpoints");
        check(packed[0] == 9.0f && packed[1] == 18.0f && packed[2] == 27.0f, "camera-relative minimum");
        check(packed[3] == 1.0f && packed[4] == 0.05f && packed[5] == 0.05f && packed[6] == 1.0f, "red RGBA marker");
        check(packed[7] == 10.0f && packed[8] == 18.0f && packed[9] == 27.0f, "first edge endpoint");
        check(LodSeamFailureOverlay.build(List.of(), 0, 0, 0).length == 0, "empty collection");
        check(LodSeamFailureOverlay.build(null, 0, 0, 0).length == 0, "null collection");
        check(marker.reason().equals("boundary-missing") && marker.revision() == 7, "failure metadata preserved");
        check(new LodSeamFailureMarker(key, 8, bounds, " ").reason().equals("unknown"), "blank reason normalized");
        System.out.println("LodSeamFailureOverlaySmokeTest: PASS (8 assertions)");
    }
}
