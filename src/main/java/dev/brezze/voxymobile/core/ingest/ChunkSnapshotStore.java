package dev.brezze.voxymobile.core.ingest;

import dev.brezze.voxymobile.core.lod.LodNodeTree;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe snapshot store with an incrementally maintained sparse LOD hierarchy. */
public final class ChunkSnapshotStore {
    private final ConcurrentHashMap<SectionPos, VoxelSection> sections = new ConcurrentHashMap<>();
    private final LodNodeTree lodTree = new LodNodeTree();

    public synchronized void put(VoxelSection section) {
        sections.put(section.position(), section);
        lodTree.put(section);
    }

    public synchronized boolean removeSection(SectionPos position) {
        if (sections.remove(position) == null) return false;
        lodTree.remove(position);
        return true;
    }

    public VoxelSection get(SectionPos position) { return sections.get(position); }
    public int size() { return sections.size(); }
    public Collection<VoxelSection> snapshot() { return List.copyOf(sections.values()); }

    public synchronized int removeChunk(int chunkX, int chunkZ) {
        int removed = 0;
        for (SectionPos pos : sections.keySet()) {
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

    /** Read-only-by-convention hierarchy handle; its own operations are synchronized. */
    public LodNodeTree lodTree() { return lodTree; }
}
