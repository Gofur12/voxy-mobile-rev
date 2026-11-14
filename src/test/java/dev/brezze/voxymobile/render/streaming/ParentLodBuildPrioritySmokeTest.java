package dev.brezze.voxymobile.render.streaming;

public final class ParentLodBuildPrioritySmokeTest {
    public static void main(String[] args) {
        double base = 4096.0;
        check(ParentLodBuildPriority.apply(base, 0) == base, "leaf-level baseline unchanged");
        double nearParent = ParentLodBuildPriority.apply(base, 1);
        double farParent = ParentLodBuildPriority.apply(base, 6);
        check(nearParent < base, "parent receives coarse priority bias");
        check(farParent < nearParent, "higher parent level is scheduled earlier");
        check(ParentLodBuildPriority.apply(base, 20) > 0.0, "maximum hierarchy level remains finite");
        check(ParentLodBuildPriority.apply(base, 21) == Double.MAX_VALUE, "invalid level rejected");
        check(ParentLodBuildPriority.apply(-1.0, 1) == Double.MAX_VALUE, "invalid priority rejected");
        System.out.println("ParentLodBuildPrioritySmokeTest: PASS (6 cases)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
