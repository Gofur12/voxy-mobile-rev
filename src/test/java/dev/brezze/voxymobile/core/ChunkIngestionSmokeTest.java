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
        if (store.retainChunk(-2, 9) != 1 || store.get(section.position()) == null
                || store.lodTree().findLeaf(section.position()) == null)
            throw new AssertionError("chunk unload retention lost far-field terrain");
        if (store.removeChunk(-2, 9) != 1 || !store.lodTree().isEmpty())
            throw new AssertionError("explicit chunk invalidation did not prune LOD tree");
        store.put(section);
        store.clear();

        ChunkSnapshotStore bounded = new ChunkSnapshotStore(2);
        VoxelSection a = SectionSnapshotBuilder.build(new SectionPos(0, 0, 0), ids);
        VoxelSection b = SectionSnapshotBuilder.build(new SectionPos(1, 0, 0), ids);
        VoxelSection c = SectionSnapshotBuilder.build(new SectionPos(2, 0, 0), ids);
        bounded.put(a); bounded.put(b);
        bounded.get(a.position()); // make A newer than B
        bounded.put(c);
        if (bounded.size() != 2 || bounded.get(a.position()) == null || bounded.get(b.position()) != null
                || bounded.lodTree().findLeaf(b.position()) != null || bounded.lodTree().findLeaf(c.position()) == null)
            throw new AssertionError("LRU memory cap did not evict oldest section and prune hierarchy");
        if (!store.lodTree().isEmpty()) throw new AssertionError("store clear did not clear LOD tree");
        System.out.println("ChunkIngestionSmokeTest: PASS");
    }
}
