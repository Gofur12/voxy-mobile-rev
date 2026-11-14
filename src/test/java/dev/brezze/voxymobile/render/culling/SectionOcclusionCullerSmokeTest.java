package dev.brezze.voxymobile.render.culling;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class SectionOcclusionCullerSmokeTest {
    public static void main(String[] args) {
        SectionPos centerPos = new SectionPos(0, 0, 0);
        VoxelSection center = section(centerPos, false, -1);
        Map<SectionPos, VoxelSection> sections = new HashMap<>();
        sections.put(centerPos, center);
        SectionPos[] neighborPositions = {
                new SectionPos(-1, 0, 0), new SectionPos(1, 0, 0),
                new SectionPos(0, -1, 0), new SectionPos(0, 1, 0),
                new SectionPos(0, 0, -1), new SectionPos(0, 0, 1)
        };
        for (SectionPos pos : neighborPositions) sections.put(pos, section(pos, true, -1));

        SectionOcclusionCuller culler = new SectionOcclusionCuller();
        require(culler.isOccluded(center, sections), "fully enclosed section was not culled");
        require(culler.isOccluded(center, sections), "cached enclosed result changed");

        SectionPos east = neighborPositions[1];
        sections.put(east, section(east, true, 0));
        require(!culler.isOccluded(center, sections), "boundary hole should prevent occlusion");

        sections.remove(east);
        require(!culler.isOccluded(center, sections), "missing neighbor must remain visible");

        culler.retain(Set.of(centerPos));
        require(culler.cachedSectionCount() == 1, "cache retain did not remove unloaded entries");
        culler.clear();
        require(culler.cachedSectionCount() == 0, "cache clear failed");
        System.out.println("SectionOcclusionCullerSmokeTest: PASS");
    }

    private static VoxelSection section(SectionPos pos, boolean solid, int holeIndex) {
        int[] palette = {0, 1};
        short[] data = new short[VoxelSection.VOLUME];
        if (solid) {
            java.util.Arrays.fill(data, (short) 1);
            if (holeIndex >= 0) data[holeIndex] = 0;
        }
        return new VoxelSection(pos, palette, data);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
