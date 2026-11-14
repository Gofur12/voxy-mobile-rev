package dev.brezze.voxymobile.render.geometry;

public final class LodSeamAsyncBuildQueueSmokeTest {
    public static void main(String[] args) throws Exception {
        LodSeamAsyncBuildQueue queue = new LodSeamAsyncBuildQueue(1, 2);
        LodSeamKey key = new LodSeamKey(11L, 22L);
        if (!queue.submit(key, 7L, () -> null)) throw new AssertionError("submit rejected");
        if (queue.submit(key, 8L, () -> null)) throw new AssertionError("duplicate key accepted");
        long until = System.nanoTime() + 2_000_000_000L;
        LodSeamAsyncBuildQueue.Completed result = null;
        while (result == null && System.nanoTime() < until) { result = queue.poll(); Thread.sleep(2L); }
        if (result == null || !result.key().equals(key) || result.revision() != 7L) throw new AssertionError("completion missing");
        if (queue.pendingCount() != 0) throw new AssertionError("pending key leaked");
        queue.close();
        System.out.println("LodSeamAsyncBuildQueueSmokeTest PASS (4 assertions)");
    }
}
