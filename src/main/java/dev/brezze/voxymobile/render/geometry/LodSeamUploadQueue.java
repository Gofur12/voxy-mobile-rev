package dev.brezze.voxymobile.render.geometry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, revision-aware CPU-to-render-thread handoff for seam mesh payloads. */
public final class LodSeamUploadQueue {
    private final int capacity;
    private final LinkedHashMap<LodSeamKey, LodSeamGpuMeshData> pending = new LinkedHashMap<>();
    private long rejectedCount;

    public LodSeamUploadQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized boolean offer(LodSeamGpuMeshData data) {
        if (data == null) throw new IllegalArgumentException("data is null");
        LodSeamGpuMeshData old = pending.get(data.key());
        if (old != null) {
            if (old.contentRevision() > data.contentRevision()) return false;
            pending.put(data.key(), data);
            return true;
        }
        if (pending.size() >= capacity) { rejectedCount++; return false; }
        pending.put(data.key(), data);
        return true;
    }

    /** Poll only from the Minecraft render thread; caller performs actual GLES upload. */
    public synchronized LodSeamGpuMeshData poll() {
        if (pending.isEmpty()) return null;
        Map.Entry<LodSeamKey, LodSeamGpuMeshData> first = pending.entrySet().iterator().next();
        pending.remove(first.getKey());
        return first.getValue();
    }

    public synchronized boolean remove(LodSeamKey key) { return pending.remove(key) != null; }
    public synchronized void clear() { pending.clear(); }
    /** Returns the queued revision for a key, or -1 when no payload is pending. */
    public synchronized long pendingRevision(LodSeamKey key) {
        LodSeamGpuMeshData data = pending.get(key);
        return data == null ? -1L : data.contentRevision();
    }

    public synchronized int size() { return pending.size(); }
    public synchronized long rejectedCount() { return rejectedCount; }
}
