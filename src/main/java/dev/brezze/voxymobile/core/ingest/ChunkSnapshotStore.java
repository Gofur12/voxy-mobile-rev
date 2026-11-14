package dev.brezze.voxymobile.core.ingest;

import dev.brezze.voxymobile.core.lod.LodNodeTree;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded snapshot store with an incrementally maintained sparse LOD hierarchy.
 * Chunk unload does not erase terrain: snapshots remain available for far-field LOD
 * until the mobile memory cap evicts the least-recently-used sections.
 */
public final class ChunkSnapshotStore {
    public static final int DEFAULT_MAX_SECTIONS = 8192;
    private final int maxSections;
    private final LinkedHashMap<SectionPos, VoxelSection> sections = new LinkedHashMap<>(256, 0.75f, true);
    private final LodNodeTree lodTree = new LodNodeTree();

    public ChunkSnapshotStore() { this(DEFAULT_MAX_SECTIONS); }

    public ChunkSnapshotStore(int maxSections) {
        if (maxSections < 1) throw new IllegalArgumentException("maxSections must be positive");
        this.maxSections = maxSections;
    }

    public synchronized void put(VoxelSection section) {
        sections.put(section.position(), section);
        lodTree.put(section);
        evictToBudget();
    }

    public synchronized boolean removeSection(SectionPos position) {
        if (sections.remove(position) == null) return false;
        lodTree.remove(position);
        return true;
    }

    /** Access updates LRU order so recently used terrain survives pressure. */
    public synchronized VoxelSection get(SectionPos position) { return sections.get(position); }

    public synchronized int size() { return sections.size(); }
    public synchronized int maxSections() { return maxSections; }
    public synchronized Collection<VoxelSection> snapshot() { return List.copyOf(sections.values()); }

    /**
     * Called when Minecraft unloads a chunk. Keep its captured terrain in the LOD
     * hierarchy and refresh its LRU position; do not delete its far-field source data.
     */
    public synchronized int retainChunk(int chunkX, int chunkZ) {
        int retained = 0;
        for (Map.Entry<SectionPos, VoxelSection> entry : new ArrayList<>(sections.entrySet())) {
            SectionPos pos = entry.getKey();
            if (pos.x() == chunkX && pos.z() == chunkZ) {
                sections.get(pos);
                retained++;
            }
        }
        return retained;
    }

    /** Explicit destructive removal, reserved for invalidation and tests. */
    public synchronized int removeChunk(int chunkX, int chunkZ) {
        int removed = 0;
        for (SectionPos pos : new ArrayList<>(sections.keySet())) {
            if (pos.x() == chunkX && pos.z() == chunkZ && sections.remove(pos) != null) {
                lodTree.remove(pos);
                removed++;
            }
        }
        return removed;
    }

    public synchronized void clear() {
        sections.clear();
        lodTree.clear();
    }

    private void evictToBudget() {
        while (sections.size() > maxSections) {
            SectionPos oldest = sections.keySet().iterator().next();
            sections.remove(oldest);
            lodTree.remove(oldest);
        }
    }

    /** Read-only-by-convention hierarchy handle; its own operations are synchronized. */
    public LodNodeTree lodTree() { return lodTree; }
}
