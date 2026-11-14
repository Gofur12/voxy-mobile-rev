package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.mesh.GreedyLodMesher;
import dev.brezze.voxymobile.core.mesh.MeshQuad;
import java.util.List;

/** Converts aggregated parent voxel data into greedy CPU quads. No OpenGL calls are made here. */
public final class ParentLodMeshBuilder {
    private ParentLodMeshBuilder() {}

    public static ParentLodMeshData build(LodNode node) {
        return build(ParentVoxelAggregator.aggregate(node));
    }

    public static ParentLodMeshData build(VoxelLodData voxels) {
        if (voxels == null) return null;
        VoxelMipLevel mip = VoxelMipLevel.fromBlockIds(voxels.copyBlockIds());
        List<MeshQuad> quads = GreedyLodMesher.mesh(mip);
        return new ParentLodMeshData(voxels.origin(), voxels.level(), quads);
    }
}
