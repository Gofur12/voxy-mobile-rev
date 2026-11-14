package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.mesh.MeshQuad;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import java.util.List;

/** CPU-side parent mesh result. This is not a GPU resource and is not submitted by the renderer yet. */
public final class ParentLodMeshData {
    private final SectionPos origin;
    private final int level;
    private final List<MeshQuad> quads;

    ParentLodMeshData(SectionPos origin, int level, List<MeshQuad> quads) {
        this.origin = origin;
        this.level = level;
        this.quads = List.copyOf(quads);
    }
    public SectionPos origin() { return origin; }
    public int level() { return level; }
    public int cellBlockSize() { return 1 << level; }
    public List<MeshQuad> quads() { return quads; }
    public boolean isEmpty() { return quads.isEmpty(); }
}
