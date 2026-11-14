package dev.brezze.voxymobile.render;

import dev.brezze.voxymobile.core.ingest.SectionSnapshotBuilder;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

/** Standalone smoke test for worker-thread meshing and immutable output. */
public final class MeshBuildSchedulerSmokeTest {
    public static void main(String[] args) throws Exception {
        int[] raw = new int[4096];
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            if (y < 8) raw[x + z * 16 + y * 256] = 1;
        VoxelSection section = SectionSnapshotBuilder.build(new SectionPos(0, 0, 0), raw);
        try (MeshBuildScheduler scheduler = new MeshBuildScheduler()) {
            if (!scheduler.request(section, 0, 0.0)) throw new AssertionError("request rejected");
            if (!scheduler.request(section, 0, 0.0)) throw new AssertionError("duplicate request rejected");
            long deadline = System.nanoTime() + 5_000_000_000L;
            MeshBuildData result = null;
            while (System.nanoTime() < deadline && result == null) {
                result = scheduler.pollCompleted();
                Thread.sleep(5L);
            }
            if (result == null) throw new AssertionError("worker did not complete mesh");
            if (result.source() != section) throw new AssertionError("source identity changed");
            if (result.isEmpty()) throw new AssertionError("solid section produced empty mesh");
            if (result.indices().length % 6 != 0) throw new AssertionError("invalid quad index count");
            System.out.println("MeshBuildSchedulerSmokeTest PASS: vertices=" + result.vertices().length / 7
                    + ", indices=" + result.indices().length);
        }
    }
}
