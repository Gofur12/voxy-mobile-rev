package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Compact integer voxel grid representing one downsampled LOD level. */
public final class VoxelMipLevel {
    private final int edge;
    private final int[] blockIds;

    private VoxelMipLevel(int edge, int[] blockIds) {
        this.edge = edge;
        this.blockIds = blockIds;
    }

    public static VoxelMipLevel fromSection(VoxelSection section) {
        if (section == null) throw new IllegalArgumentException("section is null");
        int[] ids = new int[VoxelSection.VOLUME];
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    ids[index(16, x, y, z)] = section.blockId(x, y, z);
        return new VoxelMipLevel(16, ids);
    }

    /** Creates a 16^3 mip from an already aggregated immutable payload. */
    public static VoxelMipLevel fromBlockIds(int[] blockIds) {
        if (blockIds == null || blockIds.length != 16 * 16 * 16)
            throw new IllegalArgumentException("expected 16^3 block IDs");
        return new VoxelMipLevel(16, blockIds.clone());
    }

    /** Reduces each 2x2x2 neighborhood to its most frequent non-air block ID. */
    public VoxelMipLevel downsample() {
        if (edge <= 1) throw new IllegalStateException("already at coarsest mip level");
        int nextEdge = edge >> 1;
        int[] out = new int[nextEdge * nextEdge * nextEdge];
        Map<Integer, Integer> counts = new HashMap<>(8);
        for (int y = 0; y < nextEdge; y++) {
            for (int z = 0; z < nextEdge; z++) {
                for (int x = 0; x < nextEdge; x++) {
                    counts.clear();
                    for (int dy = 0; dy < 2; dy++)
                        for (int dz = 0; dz < 2; dz++)
                            for (int dx = 0; dx < 2; dx++) {
                                int id = blockIds[index(edge, x * 2 + dx, y * 2 + dy, z * 2 + dz)];
                                if (id != 0) counts.merge(id, 1, Integer::sum);
                            }
                    int bestId = 0, bestCount = 0;
                    for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                        if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() < bestId)) {
                            bestId = e.getKey(); bestCount = e.getValue();
                        }
                    }
                    out[index(nextEdge, x, y, z)] = bestId;
                }
            }
        }
        return new VoxelMipLevel(nextEdge, out);
    }

    private static int index(int edge, int x, int y, int z) { return x + edge * (z + edge * y); }
    public int edge() { return edge; }
    public int blockId(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= edge || y >= edge || z >= edge) throw new IndexOutOfBoundsException();
        return blockIds[index(edge, x, y, z)];
    }
    public int[] copyBlockIds() { return blockIds.clone(); }
    public int nonAirCount() { int n = 0; for (int id : blockIds) if (id != 0) n++; return n; }
    @Override public String toString() { return "VoxelMipLevel[edge=" + edge + ", nonAir=" + nonAirCount() + "]"; }
}
