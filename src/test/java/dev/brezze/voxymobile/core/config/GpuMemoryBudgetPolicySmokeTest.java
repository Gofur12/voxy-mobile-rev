package dev.brezze.voxymobile.core.config;

public final class GpuMemoryBudgetPolicySmokeTest {
    public static void main(String[] args) {
        check(GpuMemoryBudgetPolicy.normalize(64) == 64, "default retained");
        check(GpuMemoryBudgetPolicy.normalize(33) == 32, "round down");
        check(GpuMemoryBudgetPolicy.normalize(41) == 48, "round nearest");
        check(GpuMemoryBudgetPolicy.normalize(999) == 256, "upper clamp");
        check(GpuMemoryBudgetPolicy.normalize(-1) == 32, "lower clamp");
        check(GpuMemoryBudgetPolicy.parse(null) == 64, "missing default");
        check(GpuMemoryBudgetPolicy.parse("bad") == 64, "invalid fallback");
        check(GpuMemoryBudgetPolicy.parse("128") == 128, "valid persisted value");
        System.out.println("GpuMemoryBudgetPolicySmokeTest: 8 assertions passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
