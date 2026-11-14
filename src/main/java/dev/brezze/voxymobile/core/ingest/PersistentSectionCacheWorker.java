package dev.brezze.voxymobile.core.ingest;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded single-worker disk I/O bridge; Minecraft/render threads never touch cache files. */
public final class PersistentSectionCacheWorker implements AutoCloseable {
    private final PersistentSectionCache cache;
    private static final int MAX_LOADED_HANDOFF = 128;
    private final ArrayBlockingQueue<VoxelSection> loaded = new ArrayBlockingQueue<>(MAX_LOADED_HANDOFF);
    private final ThreadPoolExecutor io;
    private volatile boolean closed;
    private volatile boolean loadComplete;
    private volatile boolean loadFinished;
    private volatile boolean loadFailed;

    public PersistentSectionCacheWorker(Path root, String worldKey, String dimensionKey) {
        cache = new PersistentSectionCache(root, worldKey, dimensionKey);
        io = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), runnable -> {
                    Thread thread = new Thread(runnable, "VoxyMobile-TerrainCache");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public void load() { load(0.0, 0.0, 0.0); }

    public void load(double centerX, double centerY, double centerZ) {
        execute(() -> {
            boolean completedNormally = false;
            try {
                cache.loadEachNearest(PersistentSectionCache.DEFAULT_MAX_FILES, centerX, centerY, centerZ,
                        section -> {
                            if (closed || Thread.currentThread().isInterrupted())
                                throw new InterruptedException("cache worker closed");
                            loaded.put(section);
                        });
                completedNormally = !closed && !Thread.currentThread().isInterrupted();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException error) {
                loadFailed = true;
                System.err.println("[VoxyMobile] cache load failed: " + error.getMessage());
            } finally {
                loadComplete = completedNormally && !loadFailed;
                loadFinished = completedNormally || loadFailed;
            }
        });
    }

    public void save(VoxelSection section) {
        if (section == null) return;
        execute(() -> {
            try { cache.save(section); }
            catch (IOException error) { System.err.println("[VoxyMobile] cache save failed: " + error.getMessage()); }
        });
    }

    public void remove(SectionPos position) {
        if (position == null) return;
        execute(() -> {
            try { cache.remove(position); }
            catch (IOException error) { System.err.println("[VoxyMobile] cache remove failed: " + error.getMessage()); }
        });
    }

    public VoxelSection pollLoaded() { return loaded.poll(); }
    public int pendingLoaded() { return loaded.size(); }
    /** True only when the entire cache scan completed without cancellation or I/O failure. */
    public boolean isLoadComplete() { return loadComplete; }
    /** True when restore reached a terminal state, including failure. */
    public boolean isLoadFinished() { return loadFinished; }
    public boolean isLoadFailed() { return loadFailed; }

    private void execute(Runnable task) {
        if (closed) return;
        try { io.execute(task); }
        catch (RuntimeException error) {
            System.err.println("[VoxyMobile] cache task rejected: " + error.getMessage());
        }
    }

    @Override public void close() {
        closed = true;
        io.shutdownNow();
    }
}
