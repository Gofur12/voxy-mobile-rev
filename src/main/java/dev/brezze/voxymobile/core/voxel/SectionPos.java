package dev.brezze.voxymobile.core.voxel;

/** Integer position of a 16x16x16 section in section coordinates. */
public record SectionPos(int x, int y, int z) {
    public SectionPos parent() { return new SectionPos(Math.floorDiv(x, 2), Math.floorDiv(y, 2), Math.floorDiv(z, 2)); }
    public int childIndex() { return (Math.floorMod(x, 2)) | (Math.floorMod(y, 2) << 1) | (Math.floorMod(z, 2) << 2); }
    public long packed() {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF);
    }
}
