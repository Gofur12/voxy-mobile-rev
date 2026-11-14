package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

/** Immutable 16^3 voxel payload for one hierarchy node. Each cell spans 2^level blocks. */
public final class VoxelLodData {
    public static final int EDGE = 16;
    public static final int VOLUME = EDGE * EDGE * EDGE;
    private final SectionPos origin;
    private final int level;
    private final int[] blockIds;

    public VoxelLodData(SectionPos origin, int level, int[] blockIds) {
        if (origin == null || blockIds == null || level < 0 || level > 20 || blockIds.length != VOLUME)
            throw new IllegalArgumentException("invalid LOD voxel data");
        this.origin = origin;
        this.level = level;
        this.blockIds = blockIds.clone();
    }

    public static VoxelLodData fromSection(VoxelSection section) {
        if (section == null) throw new IllegalArgumentException("section is null");
        int[] ids = new int[VOLUME];
        for (int y = 0; y < EDGE; y++)
            for (int z = 0; z < EDGE; z++)
                for (int x = 0; x < EDGE; x++) ids[index(x, y, z)] = section.blockId(x, y, z);
        return new VoxelLodData(section.position(), 0, ids);
    }

    public SectionPos origin() { return origin; }
    public int level() { return level; }
    public int cellBlockSize() { return 1 << level; }
    public int blockId(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= EDGE || y >= EDGE || z >= EDGE) throw new IndexOutOfBoundsException();
        return blockIds[index(x, y, z)];
    }
    public int[] copyBlockIds() { return blockIds.clone(); }
    public int nonAirCount() { int n = 0; for (int id : blockIds) if (id != 0) n++; return n; }
    private static int index(int x, int y, int z) { return x + EDGE * (z + EDGE * y); }
}
