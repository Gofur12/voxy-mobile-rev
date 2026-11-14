package dev.brezze.voxymobile.core.lod;

public final class LodSelectorSmokeTest {
    public static void main(String[] args) {
        check(LodSelector.select(55.0 * 55.0, -1) == 0, "initial LOD 0");
        check(LodSelector.select(56.0 * 56.0, -1) == 1, "initial LOD 1 boundary");
        check(LodSelector.select(192.0 * 192.0, -1) == 3, "initial LOD 3 boundary");
        check(LodSelector.select(60.0 * 60.0, 0) == 0, "hysteresis holds LOD 0");
        check(LodSelector.select(64.0 * 64.0, 0) == 1, "hysteresis exits LOD 0");
        check(LodSelector.select(52.0 * 52.0, 1) == 1, "hysteresis holds LOD 1");
        check(LodSelector.select(47.0 * 47.0, 1) == 0, "hysteresis returns to LOD 0");
        check(LodSelector.select(116.0 * 116.0, 1) == 1, "hysteresis holds LOD 1 upper edge");
        check(LodSelector.select(120.0 * 120.0, 1) == 2, "hysteresis enters LOD 2");
        boolean rejected = false;
        try { LodSelector.select(-1.0, -1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid distance rejected");
        System.out.println("LodSelectorSmokeTest: PASS");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
