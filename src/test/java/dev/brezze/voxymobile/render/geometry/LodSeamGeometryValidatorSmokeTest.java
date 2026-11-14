package dev.brezze.voxymobile.render.geometry;

public final class LodSeamGeometryValidatorSmokeTest {
    private static int assertions;
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        float[] good = {
                0,0,0, 1,0,0,1,
                1,0,0, 0,1,0,1,
                0,1,0, 0,0,1,1
        };
        int[] triangle = {0,1,2};
        LodSeamGeometryValidator.Report valid = LodSeamGeometryValidator.validate(good, triangle);
        check(valid.valid() && "valid".equals(valid.status()), "valid triangle accepted");
        check(valid.vertexCount() == 3 && valid.triangleCount() == 1, "counts reported");
        check(valid.byteSize() == good.length * 4L + triangle.length * 4L, "GPU byte size reported");

        float[] nan = good.clone(); nan[0] = Float.NaN;
        check("non-finite-vertex".equals(LodSeamGeometryValidator.validate(nan, triangle).status()), "NaN rejected");
        float[] badColor = good.clone(); badColor[3] = 1.1f;
        check("rgba-out-of-range".equals(LodSeamGeometryValidator.validate(badColor, triangle).status()), "RGBA range enforced");
        check("index-out-of-range".equals(LodSeamGeometryValidator.validate(good, new int[]{0,1,3}).status()), "bad index rejected");
        check("index-layout".equals(LodSeamGeometryValidator.validate(good, new int[]{0,1}).status()), "non-triangle index count rejected");
        check("degenerate-triangle".equals(LodSeamGeometryValidator.validate(good, new int[]{0,0,2}).status()), "repeated-index triangle rejected");
        float[] flat = good.clone(); flat[14] = 0; flat[15] = 0; flat[16] = 0;
        check("degenerate-triangle".equals(LodSeamGeometryValidator.validate(flat, triangle).status()), "zero-area triangle rejected");
        check("null-payload".equals(LodSeamGeometryValidator.validate(null, triangle).status()), "null vertices rejected");
        check("vertex-layout".equals(LodSeamGeometryValidator.validate(new float[]{1,2}, triangle).status()), "bad vertex stride rejected");
        System.out.println("LodSeamGeometryValidatorSmokeTest PASS (10 assertions)");
    }
}
