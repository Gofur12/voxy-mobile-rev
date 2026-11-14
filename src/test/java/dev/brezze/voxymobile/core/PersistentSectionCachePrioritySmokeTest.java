package dev.brezze.voxymobile.core;

import dev.brezze.voxymobile.core.ingest.ChunkSnapshotStore;
import dev.brezze.voxymobile.core.ingest.PersistentSectionCache;
import dev.brezze.voxymobile.core.ingest.SectionSnapshotBuilder;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class PersistentSectionCachePrioritySmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("voxymobile-cache-priority");
        try {
            int[] blocks = new int[VoxelSection.VOLUME];
            blocks[0] = 5;
            PersistentSectionCache cache = new PersistentSectionCache(root, "world", "overworld");
            SectionPos near = new SectionPos(0, 0, 0);
            SectionPos middle = new SectionPos(2, 0, 0);
            SectionPos far = new SectionPos(10, 0, 0);
            cache.save(SectionSnapshotBuilder.build(far, blocks));
            cache.save(SectionSnapshotBuilder.build(middle, blocks));
            cache.save(SectionSnapshotBuilder.build(near, blocks));

            ChunkSnapshotStore restored = new ChunkSnapshotStore(16);
            if (cache.loadAll(restored, 16, 8.0, 8.0, 8.0) != 3)
                throw new AssertionError("not all cached sections restored");
            List<VoxelSection> ordered = List.copyOf(restored.snapshot());
            if (!ordered.get(0).position().equals(near)
                    || !ordered.get(1).position().equals(middle)
                    || !ordered.get(2).position().equals(far))
                throw new AssertionError("cache restore was not nearest-first: "
                        + ordered.stream().map(VoxelSection::position).toList());
            ChunkSnapshotStore limited = new ChunkSnapshotStore(16);
            cache.loadAll(limited, 1, 8.0, 8.0, 8.0);
            if (limited.snapshot().iterator().next().position().equals(far))
                throw new AssertionError("nearest restore budget selected a distant section");
            System.out.println("PersistentSectionCachePrioritySmokeTest: PASS");
        } finally {
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }
}
