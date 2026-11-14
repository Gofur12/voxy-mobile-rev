package dev.brezze.voxymobile.render.culling;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SectionVisibilityPipelineSmokeTest {
    public static void main(String[] args) {
        SectionOcclusionCuller occlusion = new SectionOcclusionCuller();
        SectionVisibilityPipeline pipeline = new SectionVisibilityPipeline(occlusion);
        Map<SectionPos, VoxelSection> snapshots = new HashMap<>();
        Set<SectionPos> meshes = new HashSet<>();
        SectionPos near = new SectionPos(0, 0, 0);
        SectionPos farther = new SectionPos(1, 0, 0);
        SectionPos outside = new SectionPos(8, 0, 0);
        add(snapshots, meshes, near);
        add(snapshots, meshes, farther);
        add(snapshots, meshes, outside);

        // Camera-relative clip cube spans approximately +/-100 world units.
        FrustumCuller frustum = FrustumCuller.from(new Matrix4f().scaling(0.01f));
        var result = pipeline.collect(meshes, snapshots, 8, 8, 8, frustum, 256.0);
        require(result.size() == 2, "expected two visible sections, got " + result.size());
        require(result.get(0).position().equals(near), "visible sections must be sorted near-to-far");
        require(result.get(1).position().equals(farther), "farther section ordering incorrect");
        require(pipeline.lastStats().rejectedGroups() >= 1, "coarse group should reject distant group");
        require(pipeline.lastStats().visibleSections() == 2, "visible statistics mismatch");

        var limited = pipeline.collect(meshes, snapshots, 8, 8, 8, frustum, 10.0);
        require(limited.size() == 1, "distance limit should retain only nearest section");
        pipeline.clear();
        require(pipeline.lastStats().visibleSections() == 0, "clear should reset statistics");
        System.out.println("SectionVisibilityPipelineSmokeTest: PASS");
    }

    private static void add(Map<SectionPos, VoxelSection> snapshots, Set<SectionPos> meshes, SectionPos pos) {
        snapshots.put(pos, new VoxelSection(pos, new int[]{0}, new short[VoxelSection.VOLUME]));
        meshes.add(pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
