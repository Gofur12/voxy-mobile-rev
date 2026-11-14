package dev.brezze.voxymobile.core.mesh;

import dev.brezze.voxymobile.core.lod.VoxelMipLevel;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.Arrays;

public final class GreedyLodMesherSmokeTest {
    public static void main(String[] args) {
        int[] palette = {0, 7};
        short[] indices = new short[VoxelSection.VOLUME];
        Arrays.fill(indices, (short) 1);
        VoxelMipLevel mip = VoxelMipLevel.fromSection(new VoxelSection(new SectionPos(0,0,0), palette, indices));
        var quads = GreedyLodMesher.mesh(mip);
        if (quads.size() != 6) throw new AssertionError("solid cube should produce 6 quads, got " + quads.size());
        for (MeshQuad q : quads) {
            if (q.material != 7 || q.width != 16 || q.height != 16) throw new AssertionError("invalid merged quad");
        }
        System.out.println("GreedyLodMesherSmokeTest PASS");
    }
}
