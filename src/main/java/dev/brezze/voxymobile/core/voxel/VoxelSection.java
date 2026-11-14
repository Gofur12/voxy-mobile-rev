package dev.brezze.voxymobile.core.voxel;

import java.util.Arrays;

/** Immutable compact section snapshot. IDs are local palette indices, 0 is air. */
public final class VoxelSection {
    public static final int EDGE = 16;
    public static final int VOLUME = EDGE * EDGE * EDGE;
    private final SectionPos position;
    private final int[] palette;
    private final short[] indices;

    public VoxelSection(SectionPos position, int[] palette, short[] indices) {
        if (position == null || palette == null || indices == null) throw new IllegalArgumentException("null section data");
        if (palette.length == 0 || indices.length != VOLUME) throw new IllegalArgumentException("invalid section dimensions");
        this.position = position;
        this.palette = palette.clone();
        this.indices = indices.clone();
        for (short index : this.indices) if (Short.toUnsignedInt(index) >= palette.length) throw new IllegalArgumentException("palette index out of range");
    }

    public SectionPos position() { return position; }
    public int paletteSize() { return palette.length; }
    public int blockId(int x, int y, int z) {
        if ((x | y | z) < 0 || x >= EDGE || y >= EDGE || z >= EDGE) throw new IndexOutOfBoundsException();
        int packedIndex = x | (z << 4) | (y << 8);
        return palette[Short.toUnsignedInt(indices[packedIndex])];
    }
    public int[] paletteCopy() { return palette.clone(); }
    public short[] indicesCopy() { return indices.clone(); }
    public int nonAirCount() {
        int count = 0;
        for (short index : indices) if (Short.toUnsignedInt(index) != 0) count++;
        return count;
    }
    @Override public String toString() { return "VoxelSection[" + position + ", palette=" + palette.length + ", nonAir=" + nonAirCount() + "]"; }
}
