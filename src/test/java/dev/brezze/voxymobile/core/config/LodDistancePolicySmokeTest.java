package dev.brezze.voxymobile.core.config;

/** Stage 69: validates practical default and bounded independent far-LOD radius parsing. */
public final class LodDistancePolicySmokeTest {
    public static void main(String[] args) {
        check(LodDistancePolicy.DEFAULT_DISTANCE == 4096, "default LOD radius is 4096 blocks");
        check(LodDistancePolicy.parse(null) == 4096, "missing setting uses 4096-block default");
        check(LodDistancePolicy.parse(" 6144 ") == 6144, "valid configured radius is preserved");
        check(LodDistancePolicy.parse("8192") == 8192, "maximum far radius is accepted");
        check(LodDistancePolicy.parse("99999") == 8192, "radius is capped at 8192");
        check(LodDistancePolicy.parse("1") == 256, "radius is clamped to minimum");
        check(LodDistancePolicy.parse("invalid") == 4096, "invalid setting falls back safely");
        System.out.println("LodDistancePolicySmokeTest: PASS");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
