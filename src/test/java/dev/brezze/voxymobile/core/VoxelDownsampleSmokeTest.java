package dev.brezze.voxymobile.core;

import dev.brezze.voxymobile.core.lod.VoxelMipLevel;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

public final class VoxelDownsampleSmokeTest {
    public static void main(String[] args) {
        int[] palette = {0, 7, 9};
        short[] indices = new short[VoxelSection.VOLUME];
        // A solid 2x2x2 cube should reduce to one non-air voxel.
        for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++)
            indices[x | (z << 4) | (y << 8)] = 1;
        VoxelSection section = new VoxelSection(new SectionPos(0, 0, 0), palette, indices);
        VoxelMipLevel mip = VoxelMipLevel.fromSection(section).downsample();
        if (mip.edge() != 8 || mip.blockId(0, 0, 0) != 7 || mip.nonAirCount() != 1)
            throw new AssertionError("first downsample incorrect: " + mip);
        VoxelMipLevel coarse = mip.downsample().downsample().downsample();
        if (coarse.edge() != 1 || coarse.blockId(0, 0, 0) != 7)
            throw new AssertionError("coarsest mip incorrect");
        System.out.println("VoxyMobile Reborn Stage 03 downsample smoke test: PASS");
    }
}
