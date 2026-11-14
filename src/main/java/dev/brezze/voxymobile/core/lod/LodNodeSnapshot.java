package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

/** Immutable structural snapshot; leaf voxel payloads are immutable and safe to read on workers. */
public final class LodNodeSnapshot {
    private final SectionPos origin;
    private final int level;
    private final VoxelSection source;
    private final LodNodeSnapshot[] children;

    public LodNodeSnapshot(SectionPos origin, int level, VoxelSection source, LodNodeSnapshot[] children) {
        if (origin == null || level < 0 || level > 20 || children == null || children.length != 8)
            throw new IllegalArgumentException("invalid node snapshot");
        if (level > 0 && source != null) throw new IllegalArgumentException("parent snapshot cannot own a section source");
        if (source != null && !source.position().equals(origin)) throw new IllegalArgumentException("source origin mismatch");
        this.origin = origin;
        this.level = level;
        this.source = source;
        this.children = children.clone();
        if (level == 0) for (LodNodeSnapshot child : children) if (child != null)
            throw new IllegalArgumentException("leaf snapshot cannot have children");
    }

    public SectionPos origin() { return origin; }
    public int level() { return level; }
    public VoxelSection source() { return source; }
    public LodNodeSnapshot child(int index) { return children[index]; }
}
