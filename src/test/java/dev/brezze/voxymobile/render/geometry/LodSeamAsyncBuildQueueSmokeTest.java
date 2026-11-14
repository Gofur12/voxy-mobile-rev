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
            CountDownLatch thirdStarted = new CountDownLatch(1);
            LodSeamKey first = new LodSeamKey(31L, 32L);
            LodSeamKey second = new LodSeamKey(41L, 42L);
            LodSeamKey third = new LodSeamKey(51L, 52L);
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
            if (!queue.submit(third, 1L, () -> {
                thirdStarted.countDown();
                return null;
            })) throw new AssertionError("third bounded submit rejected");
            releaseFirst.countDown();
            if (!secondStarted.await(2L, TimeUnit.SECONDS)) throw new AssertionError("first queued work did not run");
            if (!thirdStarted.await(2L, TimeUnit.SECONDS)) throw new AssertionError("second queued work did not run");
            until = System.nanoTime() + 2_000_000_000L;
            while (queue.pendingCount() != 0 && System.nanoTime() < until) Thread.sleep(2L);
            if (queue.pendingCount() != 0) throw new AssertionError("pending jobs did not finish");
            if (queue.completedCount() != 2) throw new AssertionError("completed payload queue did not stay at capacity");
            if (queue.droppedCompletionCount() != 1L) throw new AssertionError("overflow was not counted");
            if (queue.poll() == null || queue.poll() == null) throw new AssertionError("retained completions missing");
            System.out.println("LodSeamAsyncBuildQueueSmokeTest PASS (11 assertions)");
        } finally {
            queue.close();
        }
    }
}
