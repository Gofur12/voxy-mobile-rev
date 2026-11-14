package dev.brezze.voxymobile.render.geometry;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Set;

/** Bounded CPU-only seam builder. Completed payloads must be consumed on the render thread. */
public final class LodSeamAsyncBuildQueue implements AutoCloseable {
    public static final class Completed {
        private final LodSeamKey key; private final long revision; private final LodSeamLiveProducer.Result result;
        private Completed(LodSeamKey key, long revision, LodSeamLiveProducer.Result result) { this.key=key; this.revision=revision; this.result=result; }
        public LodSeamKey key() { return key; } public long revision() { return revision; }
        public LodSeamLiveProducer.Result result() { return result; }
    }
    private final ThreadPoolExecutor executor;
    private final Set<LodSeamKey> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Completed> completed = new ConcurrentLinkedQueue<>();
    public LodSeamAsyncBuildQueue(int workers, int capacity) {
        if (workers < 1 || capacity < 1) throw new IllegalArgumentException("workers and capacity must be positive");
        executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), r -> { Thread t=new Thread(r, "VoxyMobile-SeamCPU"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }
    public boolean submit(LodSeamKey key, long revision, Callable<LodSeamLiveProducer.Result> build) {
        if (key == null || build == null) throw new IllegalArgumentException("key and build are required");
        if (!pending.add(key)) return false;
        try { executor.execute(() -> {
            try { completed.add(new Completed(key, revision, build.call())); }
            catch (Exception error) { completed.add(new Completed(key, revision, null)); }
            finally { pending.remove(key); }
        }); return true; }
        catch (java.util.concurrent.RejectedExecutionException full) { pending.remove(key); return false; }
    }
    public Completed poll() { return completed.poll(); }
    public boolean isPending(LodSeamKey key) { return pending.contains(key); }
    public int pendingCount() { return pending.size(); }
    public int completedCount() { return completed.size(); }
    public void remove(LodSeamKey key) { if (key != null) completed.removeIf(item -> item.key().equals(key)); }
    @Override public void close() { executor.shutdownNow(); pending.clear(); completed.clear(); }
}
