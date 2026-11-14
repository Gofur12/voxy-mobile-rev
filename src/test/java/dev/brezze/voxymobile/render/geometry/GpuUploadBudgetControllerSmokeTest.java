package dev.brezze.voxymobile.render.geometry;

public final class GpuUploadBudgetControllerSmokeTest {
    public static void main(String[] args) {
        GpuUploadBudgetController budget = new GpuUploadBudgetController();
        budget.beginFrame(0L, 0);
        long twoMb = GpuUploadBudgetController.DEFAULT_BYTES_PER_FRAME;
        check(budget.frameBudgetBytes() == twoMb, "normal pressure budget");
        check(budget.tryConsume(twoMb / 2), "first half accepted");
        check(budget.tryConsume(twoMb / 2), "second half accepted");
        check(!budget.tryConsume(1), "over-budget upload rejected");
        check(budget.uploadedBytesThisFrame() == twoMb, "frame byte accounting");

        budget.beginFrame(1L, 2);
        check(budget.frameBudgetBytes() == twoMb / 2, "high pressure halves budget");
        check(budget.tryConsume(twoMb), "single oversized mesh does not starve");
        check(!budget.tryConsume(1), "oversized mesh blocks later uploads this frame");

        budget.beginFrame(2L, 3);
        check(budget.frameBudgetBytes() == 512L * 1024L, "critical pressure minimum budget");
        check(budget.tryConsume(0), "empty mesh consumes no budget");
        check(budget.uploadedBytesThisFrame() == 0, "empty mesh does not change accounting");

        budget.beginFrame(0L, 1);
        check(budget.frameBudgetBytes() == twoMb * 3L / 4L, "moderate pressure budget");
        check(budget.remainingBytesThisFrame() == budget.frameBudgetBytes(), "new frame resets budget");

        expectFailure(() -> budget.beginFrame(-1, 0), "negative allocation rejected");
        expectFailure(() -> budget.beginFrame(0, 4), "invalid pressure rejected");
        expectFailure(() -> budget.tryConsume(-1), "negative payload rejected");
        System.out.println("GpuUploadBudgetControllerSmokeTest: 4 cases passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
}
