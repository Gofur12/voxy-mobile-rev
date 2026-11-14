package dev.brezze.voxymobile.render.geometry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class LodSeamAsyncBuildQueueSmokeTest {
    public static void main(String[] args) throws Exception {
        LodSeamAsyncBuildQueue queue = new LodSeamAsyncBuildQueue(1, 2);
        try {
            LodSeamKey key = new LodSeamKey(11L, 22L);
            if (!queue.submit(key, 7L, () -> null)) throw new AssertionError("submit rejected");
            if (queue.submit(key, 8L, () -> null)) throw new AssertionError("duplicate key accepted");
            long until = System.nanoTime() + 2_000_000_000L;
            LodSeamAsyncBuildQueue.Completed result = null;
            while (result == null && System.nanoTime() < until) {
                result = queue.poll();
                if (result == null) Thread.sleep(2L);
            }
            if (result == null || !result.key().equals(key) || result.revision() != 7L)
                throw new AssertionError("completion missing");
            if (queue.pendingCount() != 0) throw new AssertionError("pending key leaked");

            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            LodSeamKey first = new LodSeamKey(31L, 32L);
            LodSeamKey second = new LodSeamKey(41L, 42L);
            if (!queue.submit(first, 1L, () -> {
                firstStarted.countDown();
                if (!releaseFirst.await(2L, TimeUnit.SECONDS)) throw new AssertionError("test timeout");
                return null;
            })) throw new AssertionError("first bounded submit rejected");
            if (!firstStarted.await(2L, TimeUnit.SECONDS)) throw new AssertionError("worker did not start");
            if (!queue.submit(second, 1L, () -> {
                secondStarted.countDown();
                return null;
            })) throw new AssertionError("second bounded submit rejected");
            releaseFirst.countDown();
            if (!secondStarted.await(2L, TimeUnit.SECONDS)) throw new AssertionError("queued work did not run");
            until = System.nanoTime() + 2_000_000_000L;
            while (queue.pendingCount() != 0 && System.nanoTime() < until) Thread.sleep(2L);
            if (queue.completedCount() != 1) throw new AssertionError("completed payload queue exceeded capacity");
            if (queue.droppedCompletionCount() != 1L) throw new AssertionError("overflow was not counted");
            if (queue.poll() == null) throw new AssertionError("retained completion missing");
            System.out.println("LodSeamAsyncBuildQueueSmokeTest PASS (9 assertions)");
        } finally {
            queue.close();
        }
    }
}
