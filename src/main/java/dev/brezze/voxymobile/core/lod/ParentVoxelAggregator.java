package dev.brezze.voxymobile.core.lod;

import java.util.HashMap;
import java.util.Map;

/** Builds a parent node by combining its eight child payloads and reducing 2x2x2 samples. */
public final class ParentVoxelAggregator {
    private ParentVoxelAggregator() {}

    /** Returns null when the node has no source data anywhere in its subtree. */
    public static VoxelLodData aggregate(LodNode node) {
        if (node == null) throw new IllegalArgumentException("node is null");
        LodNodeSnapshot snapshot = copyForAggregation(node);
        return snapshot == null ? null : aggregate(snapshot);
    }

    /** Aggregates an immutable structural snapshot; safe to call from a worker thread. */
    public static VoxelLodData aggregate(LodNodeSnapshot node) {
        if (node == null) throw new IllegalArgumentException("node is null");
        if (node.level() == 0) return node.source() == null ? null : VoxelLodData.fromSection(node.source());

        VoxelLodData[] children = new VoxelLodData[8];
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            LodNodeSnapshot child = node.child(i);
            if (child != null) {
                children[i] = aggregate(child);
                any |= children[i] != null;
            }
        }
        if (!any) return null;

        // The eight 16^3 children tile one 32^3 parent-space input volume.
        int[] combined = new int[32 * 32 * 32];
        for (int childIndex = 0; childIndex < 8; childIndex++) {
            VoxelLodData child = children[childIndex];
            if (child == null) continue; // absent child coverage is air, never fabricated solid
            if (child.level() != node.level() - 1) throw new IllegalStateException("child LOD level mismatch");
            int ox = (childIndex & 1) * 16;
            int oy = ((childIndex >>> 1) & 1) * 16;
            int oz = ((childIndex >>> 2) & 1) * 16;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                combined[index32(ox + x, oy + y, oz + z)] = child.blockId(x, y, z);
            }
        }

        int[] reduced = new int[VoxelLodData.VOLUME];
        Map<Integer, Integer> counts = new HashMap<>(8);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            counts.clear();
            for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) {
                int id = combined[index32(x * 2 + dx, y * 2 + dy, z * 2 + dz)];
                if (id != 0) counts.merge(id, 1, Integer::sum);
            }
            int bestId = 0, bestCount = 0;
            for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
                int id = entry.getKey(), count = entry.getValue();
                if (count > bestCount || (count == bestCount && (bestId == 0 || id < bestId))) {
                    bestId = id;
                    bestCount = count;
                }
            }
            reduced[index16(x, y, z)] = bestId;
        }
        return new VoxelLodData(node.origin(), node.level(), reduced);
    }

    private static LodNodeSnapshot copyForAggregation(LodNode node) {
        LodNodeSnapshot[] children = new LodNodeSnapshot[8];
        for (int i = 0; i < 8; i++) {
            LodNode child = node.child(i);
            if (child != null) children[i] = copyForAggregation(child);
        }
        if (node.level() == 0 && node.source() == null) return null;
        return new LodNodeSnapshot(node.origin(), node.level(), node.source(), children);
    }

    private static int index16(int x, int y, int z) { return x + 16 * (z + 16 * y); }
    private static int index32(int x, int y, int z) { return x + 32 * (z + 32 * y); }
}
