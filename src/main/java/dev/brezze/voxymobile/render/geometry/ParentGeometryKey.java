package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.voxel.SectionPos;

/** Stable identity for one hierarchical node's parent geometry. */
public record ParentGeometryKey(SectionPos origin, int level) {
    public ParentGeometryKey {
        if (origin == null || level <= 0 || level > 20) {
            throw new IllegalArgumentException("parent geometry requires an origin and level 1..20");
        }
    }
}
