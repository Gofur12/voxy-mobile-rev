package dev.brezze.voxymobile.core;

import dev.brezze.voxymobile.core.ingest.SectionSnapshotBuilder;
import dev.brezze.voxymobile.core.ingest.ChunkSnapshotStore;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

public final class ChunkIngestionSmokeTest {
    public static void main(String[] args) {
        int[] ids = new int[VoxelSection.VOLUME];
        ids[0] = 42;
        ids[1] = 42;
        ids[2] = 7;
        VoxelSection section = SectionSnapshotBuilder.build(new SectionPos(-2, 4, 9), ids);
        if (section.blockId(0, 0, 0) != 42 || section.blockId(1, 0, 0) != 42 || section.blockId(2, 0, 0) != 7)
            throw new AssertionError("palette decode mismatch");
        if (section.blockId(3, 0, 0) != 0) throw new AssertionError("air must map to palette index zero");
        if (section.nonAirCount() != 3) throw new AssertionError("non-air count mismatch");
        ChunkSnapshotStore store = new ChunkSnapshotStore();
        store.put(section);
        if (store.lodTree().findLeaf(section.position()) == null) throw new AssertionError("snapshot not indexed in LOD tree");
        if (!store.removeSection(section.position()) || store.get(section.position()) != null
                || !store.lodTree().isEmpty()) throw new AssertionError("empty-section removal did not prune LOD tree");
        store.put(section);
        if (store.removeChunk(-2, 9) != 1 || !store.lodTree().isEmpty()) throw new AssertionError("chunk unload did not prune LOD tree");
        store.put(section);
        store.clear();
        if (!store.lodTree().isEmpty()) throw new AssertionError("store clear did not clear LOD tree");
        System.out.println("ChunkIngestionSmokeTest: PASS");
    }
}
