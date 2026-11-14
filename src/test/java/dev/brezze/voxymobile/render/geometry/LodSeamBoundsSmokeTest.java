package dev.brezze.voxymobile.render.geometry;

/** CPU-only contract test for seam bounds and camera-relative frustum coordinates. */
public final class LodSeamBoundsSmokeTest {
    public static void main(String[] args) {
        float[] packed = {
                -2, 4, 8, 1, 1, 1, 1,
                 3, 1, 5, 1, 1, 1, 1,
                 0, 7, 2, 1, 1, 1, 1
        };
        LodSeamBounds bounds = LodSeamBounds.fromPackedVertices(packed);
        check(bounds.minX() == -2 && bounds.maxX() == 3, "X bounds span every seam vertex");
        check(bounds.minY() == 1 && bounds.maxY() == 7, "Y bounds span every seam vertex");
        check(bounds.minZ() == 2 && bounds.maxZ() == 8, "Z bounds span every seam vertex");
        float[] relative = bounds.cameraRelative(10, 2, -4);
        check(relative[0] == -12 && relative[1] == -1 && relative[2] == 6, "minimum bounds translate into camera-relative space");
        check(relative[3] == -7 && relative[4] == 5 && relative[5] == 12, "maximum bounds translate into camera-relative space");
        expectInvalid(() -> LodSeamBounds.fromPackedVertices(new float[0]), "empty vertex payload rejected");
        float[] nan = packed.clone(); nan[0] = Float.NaN;
        expectInvalid(() -> LodSeamBounds.fromPackedVertices(nan), "non-finite positions rejected");
        expectInvalid(() -> new LodSeamBounds(2, 0, 0, 1, 1, 1), "inverted bounds rejected");
        System.out.println("LodSeamBoundsSmokeTest PASS (8 assertions)");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expectInvalid(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { }
    }
}
