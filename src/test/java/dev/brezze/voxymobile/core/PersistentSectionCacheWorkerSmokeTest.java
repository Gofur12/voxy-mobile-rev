package dev.brezze.voxymobile.core;

import dev.brezze.voxymobile.core.ingest.PersistentSectionCacheWorker;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import dev.brezze.voxymobile.core.ingest.SectionSnapshotBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public final class PersistentSectionCacheWorkerSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("voxymobile-worker-test");
        try {
            int[] ids = new int[VoxelSection.VOLUME];
            ids[0] = 23;
            VoxelSection source = SectionSnapshotBuilder.build(new SectionPos(3, 5, -2), ids);
            PersistentSectionCacheWorker writer = new PersistentSectionCacheWorker(root, "world", "overworld");
            writer.save(source);
            writer.close();
            // Wait for the orderly worker shutdown to finish writing the queued snapshot.
            for (int i = 0; i < 100 && !Files.exists(root.resolve("world").resolve("overworld").resolve("3_5_-2.vms")); i++)
                TimeUnit.MILLISECONDS.sleep(20);
            PersistentSectionCacheWorker reader = new PersistentSectionCacheWorker(root, "world", "overworld");
            reader.load();
            VoxelSection restored = null;
            for (int i = 0; i < 100 && restored == null; i++) {
                restored = reader.pollLoaded();
                if (restored == null) TimeUnit.MILLISECONDS.sleep(20);
            }
            if (restored == null || restored.blockId(0, 0, 0) != 23)
                throw new AssertionError("async cache restore failed");
            reader.close();
            System.out.println("PersistentSectionCacheWorkerSmokeTest: PASS");
        } finally {
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} });
            }
        }
    }
}
