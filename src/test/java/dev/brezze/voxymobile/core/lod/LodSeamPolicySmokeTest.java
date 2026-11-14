package dev.brezze.voxymobile.core.lod;

public final class LodSeamPolicySmokeTest {
    public static void main(String[] args) {
        check(LodSeamPolicy.constrainAdjacentLevel(5, 2) == 3);
        check(LodSeamPolicy.constrainAdjacentLevel(0, 4) == 3);
        check(LodSeamPolicy.constrainAdjacentLevel(3, 4) == 3);
        check(LodSeamPolicy.constrainAdjacentLevel(5, 4) == 5);
        check(LodSeamPolicy.constrainAdjacentLevel(2, 2) == 2);
        check(!LodSeamPolicy.hasSeamRisk(2, 3));
        check(!LodSeamPolicy.hasSeamRisk(3, 2));
        check(LodSeamPolicy.hasSeamRisk(1, 4));
        check(LodSeamPolicy.requiredIntermediateLevels(1, 4) == 2);
        check(LodSeamPolicy.requiredIntermediateLevels(4, 1) == 2);
        check(LodSeamPolicy.requiredIntermediateLevels(2, 3) == 0);
        expectInvalid(-1, 2);
        expectInvalid(2, -1);
        System.out.println("LodSeamPolicySmokeTest: 13 assertions passed");
    }
    private static void expectInvalid(int a, int b) {
        try { LodSeamPolicy.constrainAdjacentLevel(a, b); throw new AssertionError("negative level accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
