package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;

/** Immutable subtree structure captured with the exact revision it represents. */
public record NodeDataSnapshot(SectionPos origin, int level, long contentRevision, LodNodeSnapshot tree) {
    public NodeDataSnapshot {
        if (origin == null || tree == null || level <= 0 || level > 20
                || !origin.equals(tree.origin()) || level != tree.level() || contentRevision < 0)
            throw new IllegalArgumentException("invalid node data snapshot");
    }
}
