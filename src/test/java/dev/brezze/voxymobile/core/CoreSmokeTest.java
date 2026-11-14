package dev.brezze.voxymobile.core;

import dev.brezze.voxymobile.core.lod.LodMath;
import dev.brezze.voxymobile.core.lod.LodNode;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

public final class CoreSmokeTest {
    public static void main(String[] args) {
        SectionPos p = new SectionPos(-1, 2, 3);
        if (!p.parent().equals(new SectionPos(-1, 1, 1))) throw new AssertionError("negative parent mapping");
        if (p.childIndex() != 5) throw new AssertionError("child index");
        int[] palette = {0, 42};
        short[] ids = new short[VoxelSection.VOLUME];
        ids[0] = 1;
        VoxelSection section = new VoxelSection(new SectionPos(0, 0, 0), palette, ids);
        if (section.blockId(0, 0, 0) != 42 || section.nonAirCount() != 1) throw new AssertionError("voxel lookup");
        LodNode root = new LodNode(new SectionPos(0, 0, 0), 1);
        root.setChild(0, new LodNode(new SectionPos(0, 0, 0), 0));
        if (root.populatedChildren() != 1 || !root.isDirty()) throw new AssertionError("LOD hierarchy");
        if (LodMath.scale(4) != 16) throw new AssertionError("LOD scale");
        System.out.println("VoxyMobile Reborn Stage 01 core smoke test: PASS");
    }
}
