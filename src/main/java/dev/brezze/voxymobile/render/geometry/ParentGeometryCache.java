package dev.brezze.voxymobile.render.geometry;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thread-safe byte-bounded LRU for immutable parent geometry awaiting/residing in the renderer. */
public final class ParentGeometryCache {
    private final long maxBytes;
    private final LinkedHashMap<ParentGeometryKey, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long currentBytes;
    private long hitCount;
    private long missCount;
    private long evictionCount;

    public ParentGeometryCache(long maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        this.maxBytes = maxBytes;
    }

    /** Returns a payload only when its source content revision exactly matches. */
    public synchronized ParentGpuMeshData get(ParentGeometryKey key, long revision) {
        Entry entry = entries.get(key);
        if (entry == null || entry.revision != revision) {
            missCount++;
            if (entry != null && entry.revision != revision) removeInternal(key);
            return null;
        }
        hitCount++;
        return entry.data;
    }

    /** Replaces any prior revision and evicts least-recently-used entries to honor the byte budget. */
    public synchronized boolean put(ParentGpuMeshData data) {
        if (data == null) throw new IllegalArgumentException("data is null");
        long size = data.byteSize();
        removeInternal(data.key());
        if (size > maxBytes) return false;
        entries.put(data.key(), new Entry(data.contentRevision(), data, size));
        currentBytes += size;
        Iterator<Map.Entry<ParentGeometryKey, Entry>> iterator = entries.entrySet().iterator();
        while (currentBytes > maxBytes && iterator.hasNext()) {
            Entry eldest = iterator.next().getValue();
            currentBytes -= eldest.bytes;
            iterator.remove();
            evictionCount++;
        }
        return entries.containsKey(data.key());
    }

    public synchronized boolean invalidate(ParentGeometryKey key) { return removeInternal(key); }
    public synchronized void clear() { entries.clear(); currentBytes = 0L; }
    public synchronized int size() { return entries.size(); }
    public synchronized long currentBytes() { return currentBytes; }
    public long maxBytes() { return maxBytes; }
    public synchronized long hitCount() { return hitCount; }
    public synchronized long missCount() { return missCount; }
    public synchronized long evictionCount() { return evictionCount; }

    private boolean removeInternal(ParentGeometryKey key) {
        Entry removed = entries.remove(key);
        if (removed == null) return false;
        currentBytes -= removed.bytes;
        return true;
    }

    private record Entry(long revision, ParentGpuMeshData data, long bytes) {}
}
