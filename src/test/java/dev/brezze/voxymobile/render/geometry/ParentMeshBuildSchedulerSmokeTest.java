package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.lod.LodNodeTree;
import dev.brezze.voxymobile.core.lod.NodeDataSnapshot;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

import java.util.Arrays;

public final class ParentMeshBuildSchedulerSmokeTest {
    public static void main(String[] args) throws Exception {
        testAtomicSnapshot();
        testAsyncBuild();
        testMultiLevelBuild();
        testStaleResultInvalidation();
        testCloseStopsWorker();
        System.out.println("ParentMeshBuildSchedulerSmokeTest: PASS (5 cases)");
    }

    private static void testAtomicSnapshot() {
        LodNodeTree tree = populatedTree();
        NodeDataSnapshot snapshot = tree.snapshotNodeData(new SectionPos(0, 0, 0), 1);
        check(snapshot != null, "parent snapshot exists");
        check(snapshot.level() == 1 && snapshot.tree().level() == 1, "level matches voxel payload");
        check(snapshot.contentRevision() == tree.findNode(new SectionPos(0, 0, 0), 1).contentRevision(), "revision captured");
    }

    private static void testAsyncBuild() throws Exception {
        LodNodeTree tree = populatedTree();
        NodeDataSnapshot snapshot = tree.snapshotNodeData(new SectionPos(0, 0, 0), 1);
        try (ParentMeshBuildScheduler scheduler = new ParentMeshBuildScheduler()) {
            check(scheduler.request(snapshot, 0), "job accepted");
            ParentGpuMeshData result = await(scheduler);
            check(result != null, "worker result available");
            check(result.key().origin().equals(snapshot.origin()), "node key retained");
            check(result.contentRevision() == snapshot.contentRevision(), "revision retained");
            check(result.indexCount() > 0, "parent geometry generated off-thread");
        }
    }


    private static void testMultiLevelBuild() throws Exception {
        LodNodeTree tree = new LodNodeTree(2);
        int[] palette = {0, 9};
        short[] indices = new short[VoxelSection.VOLUME];
        Arrays.fill(indices, (short) 1);
        for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
            tree.put(new VoxelSection(new SectionPos(x, y, z), palette, indices));
        NodeDataSnapshot root = tree.snapshotNodeData(new SectionPos(0, 0, 0), 2);
        NodeDataSnapshot child = tree.snapshotNodeData(new SectionPos(0, 0, 0), 1);
        check(root != null && child != null, "level 2 root and level 1 child snapshots exist");
        try (ParentMeshBuildScheduler scheduler = new ParentMeshBuildScheduler()) {
            check(scheduler.request(root, 400), "level 2 request accepted");
            check(scheduler.request(child, 0), "level 1 request accepted");
            ParentGpuMeshData first = await(scheduler);
            ParentGpuMeshData second = await(scheduler);
            check(first != null && second != null, "both hierarchy levels generated");
            check(first.key().level() != second.key().level(), "results retain distinct hierarchy levels");
            check((first.key().level() == 2 || second.key().level() == 2), "level 2 mesh generated");
        }
    }

    private static void testStaleResultInvalidation() throws Exception {
        LodNodeTree tree = populatedTree();
        NodeDataSnapshot snapshot = tree.snapshotNodeData(new SectionPos(0, 0, 0), 1);
        ParentGeometryKey key = new ParentGeometryKey(snapshot.origin(), snapshot.level());
        try (ParentMeshBuildScheduler scheduler = new ParentMeshBuildScheduler()) {
            check(scheduler.request(snapshot, 0), "job accepted");
            scheduler.invalidate(key);
            Thread.sleep(80);
            check(scheduler.pollCompleted() == null, "invalidated result is not published");
        }
    }

    private static void testCloseStopsWorker() {
        ParentMeshBuildScheduler scheduler = new ParentMeshBuildScheduler();
        scheduler.close();
        check(!scheduler.request(new NodeDataSnapshot(new SectionPos(0, 0, 0), 1, 1,
                new dev.brezze.voxymobile.core.lod.LodNodeSnapshot(new SectionPos(0, 0, 0), 1, null,
                        new dev.brezze.voxymobile.core.lod.LodNodeSnapshot[8])), 0), "closed scheduler rejects work");
    }

    private static ParentGpuMeshData await(ParentMeshBuildScheduler scheduler) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            ParentGpuMeshData result = scheduler.pollCompleted();
            if (result != null) return result;
            Thread.sleep(5);
        }
        return null;
    }

    private static LodNodeTree populatedTree() {
        LodNodeTree tree = new LodNodeTree(1);
        int[] palette = {0, 7};
        short[] indices = new short[VoxelSection.VOLUME];
        Arrays.fill(indices, (short) 1);
        for (int i = 0; i < 8; i++) {
            SectionPos pos = new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1);
            tree.put(new VoxelSection(pos, palette, indices));
        }
        return tree;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
