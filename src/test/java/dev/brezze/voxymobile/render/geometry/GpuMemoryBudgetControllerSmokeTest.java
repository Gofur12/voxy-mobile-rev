package dev.brezze.voxymobile.render.geometry;

/** Dependency-free Stage 23 tests for memory pressure and eviction hysteresis. */
public final class GpuMemoryBudgetControllerSmokeTest {
    public static void main(String[] args) {
        GpuMemoryBudgetController policy = new GpuMemoryBudgetController(1_000);
        check(!policy.shouldEvict(900), "exact high-water mark does not trigger");
        check(policy.shouldEvict(901), "crossing high-water mark starts eviction");
        check(policy.shouldEvict(800), "hysteresis keeps eviction active above target");
        check(!policy.shouldEvict(780), "reaching target ends eviction");
        check(policy.pressureLevel(749) == 0, "low pressure");
        check(policy.pressureLevel(750) == 1, "moderate pressure");
        check(policy.pressureLevel(900) == 2, "high pressure");
        check(policy.pressureLevel(1_000) == 3, "budget exceeded");
        check(policy.budgetBytes() == 1_000 && policy.evictionTargetBytes() == 780, "budget values");
        policy.setBudgetBytes(2_000);
        check(policy.budgetBytes() == 2_000 && policy.evictionTargetBytes() == 1_560, "live budget update");
        boolean updateRejected = false;
        try { policy.setBudgetBytes(0); } catch (IllegalArgumentException expected) { updateRejected = true; }
        check(updateRejected, "reject invalid live budget");
        boolean rejected = false;
        try { new GpuMemoryBudgetController(0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "reject invalid budget");
        System.out.println("GpuMemoryBudgetControllerSmokeTest: 12 assertions passed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
