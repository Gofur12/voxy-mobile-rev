package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.lod.LodNode;
import dev.brezze.voxymobile.core.lod.ParentLodMeshBuilder;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.Arrays;

public final class ParentGeometryPipelineSmokeTest {
    public static void main(String[] args) {
        testWorldSpacePackingAndIndices();
        testRevisionAwareCache();
        testLruEvictionAndOversizeRejection();
        testUploadQueueDedupAndCapacity();
        testQueueRejectsOlderRevision();
        System.out.println("ParentGeometryPipelineSmokeTest: PASS (5 cases)");
    }

    private static void testWorldSpacePackingAndIndices() {
        ParentGpuMeshData data = meshAt(new SectionPos(-2, 3, 4), 7L);
        check(data.key().level() == 1, "parent level retained");
        check(data.vertexCount() == 24, "six quads produce 24 vertices");
        check(data.indexCount() == 36, "six quads produce 36 indices");
        float[] vertices = data.copyVertices();
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertices.length; i += ParentGpuMeshData.FLOATS_PER_VERTEX) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
        }
        check(minX == -32.0f, "negative section origin converted to world blocks");
        check(maxX == 0.0f, "parent LOD scale applied to geometry extent");
        int[] indices = data.copyIndices();
        check(Arrays.stream(indices).min().orElse(-1) == 0, "indices start at zero");
        check(Arrays.stream(indices).max().orElse(-1) == 23, "indices address all vertices");
    }

    private static void testRevisionAwareCache() {
        ParentGeometryCache cache = new ParentGeometryCache(1_000_000);
        ParentGpuMeshData rev1 = meshAt(new SectionPos(0, 0, 0), 1L);
        check(cache.put(rev1), "first insert");
        check(cache.get(rev1.key(), 1L) == rev1, "matching revision hit");
        check(cache.get(rev1.key(), 2L) == null, "stale revision miss");
        check(cache.size() == 0, "stale entry removed");
        check(cache.hitCount() == 1 && cache.missCount() == 1, "cache metrics");
    }

    private static void testLruEvictionAndOversizeRejection() {
        ParentGpuMeshData a = meshAt(new SectionPos(0, 0, 0), 1L);
        ParentGpuMeshData b = meshAt(new SectionPos(2, 0, 0), 1L);
        ParentGeometryCache cache = new ParentGeometryCache(a.byteSize() + b.byteSize());
        check(cache.put(a) && cache.put(b), "two meshes fit");
        check(cache.get(a.key(), 1L) == a, "touch a makes it most recent");
        ParentGpuMeshData c = meshAt(new SectionPos(4, 0, 0), 1L);
        check(cache.put(c), "new mesh replaces least-recently-used b");
        check(cache.get(a.key(), 1L) == a, "recent a retained");
        check(cache.get(b.key(), 1L) == null, "least-recent b evicted");
        check(cache.evictionCount() == 1, "one LRU eviction");
        ParentGeometryCache tiny = new ParentGeometryCache(1);
        check(!tiny.put(a) && tiny.size() == 0, "oversized geometry rejected");
    }

    private static void testUploadQueueDedupAndCapacity() {
        ParentGeometryUploadQueue queue = new ParentGeometryUploadQueue(1);
        ParentGpuMeshData a1 = meshAt(new SectionPos(0, 0, 0), 1L);
        ParentGpuMeshData a2 = meshAt(new SectionPos(0, 0, 0), 2L);
        ParentGpuMeshData b = meshAt(new SectionPos(2, 0, 0), 1L);
        check(queue.offer(a1), "initial offer");
        check(queue.offer(a2), "same key replaced with newer revision");
        check(!queue.offer(b), "capacity enforced");
        check(queue.rejectedCount() == 1, "rejection metric");
        check(queue.poll() == a2, "newest queued revision polled");
        check(queue.poll() == null, "queue drained");
    }

    private static void testQueueRejectsOlderRevision() {
        ParentGeometryUploadQueue queue = new ParentGeometryUploadQueue(2);
        ParentGpuMeshData newer = meshAt(new SectionPos(0, 0, 0), 9L);
        ParentGpuMeshData older = meshAt(new SectionPos(0, 0, 0), 8L);
        check(queue.offer(newer), "newer offered");
        check(!queue.offer(older), "older revision rejected");
        check(queue.poll() == newer, "newer payload preserved");
    }

    private static ParentGpuMeshData meshAt(SectionPos origin, long revision) {
        LodNode root = new LodNode(origin, 1);
        int[] palette = {0, 5};
        short[] indices = new short[VoxelSection.VOLUME];
        Arrays.fill(indices, (short) 1);
        for (int childIndex = 0; childIndex < 8; childIndex++) {
            SectionPos childPos = new SectionPos(origin.x() + (childIndex & 1),
                    origin.y() + ((childIndex >>> 1) & 1), origin.z() + ((childIndex >>> 2) & 1));
            LodNode child = new LodNode(childPos, 0);
            child.setSource(new VoxelSection(childPos, palette, indices));
            root.setChild(childIndex, child);
        }
        return ParentGpuMeshData.from(ParentLodMeshBuilder.build(root), revision);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
