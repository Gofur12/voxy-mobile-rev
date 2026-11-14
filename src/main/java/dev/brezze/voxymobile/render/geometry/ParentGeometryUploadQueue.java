package dev.brezze.voxymobile.render.geometry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, deduplicating hand-off from CPU workers to the Minecraft render thread. */
public final class ParentGeometryUploadQueue {
    private final int capacity;
    private final LinkedHashMap<ParentGeometryKey, ParentGpuMeshData> pending = new LinkedHashMap<>();
    private long rejectedCount;

    public ParentGeometryUploadQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    /** A newer revision replaces an older queued payload for the same node. */
    public synchronized boolean offer(ParentGpuMeshData data) {
        if (data == null) throw new IllegalArgumentException("data is null");
        ParentGpuMeshData existing = pending.get(data.key());
        if (existing != null) {
            if (existing.contentRevision() > data.contentRevision()) return false;
            pending.put(data.key(), data);
            return true;
        }
        if (pending.size() >= capacity) { rejectedCount++; return false; }
        pending.put(data.key(), data);
        return true;
    }

    /** Must be polled from the render thread; this class itself never invokes GL. */
    public synchronized ParentGpuMeshData poll() {
        if (pending.isEmpty()) return null;
        Map.Entry<ParentGeometryKey, ParentGpuMeshData> first = pending.entrySet().iterator().next();
        pending.remove(first.getKey());
        return first.getValue();
    }

    public synchronized boolean remove(ParentGeometryKey key) { return pending.remove(key) != null; }
    public synchronized void clear() { pending.clear(); }
    public synchronized int size() { return pending.size(); }
    public synchronized long rejectedCount() { return rejectedCount; }
}
