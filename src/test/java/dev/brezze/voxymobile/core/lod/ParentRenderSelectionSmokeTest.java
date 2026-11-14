package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import dev.brezze.voxymobile.render.geometry.ParentGpuMeshData;

/** Stage 15 CPU contract: ready parents replace descendants; stale parents yield to ready children. */
public final class ParentRenderSelectionSmokeTest {
    public static void main(String[] args) {
        LodNodeTree tree = new LodNodeTree(1);
        for (int i = 0; i < 8; i++) {
            SectionPos pos = new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1);
            tree.put(section(pos, 1));
        }
        check(tree.nodesSnapshot().size() == 9, "snapshot contains one parent and eight leaves");
        for (LodNode node : tree.nodesSnapshot())
            if (node.level() == 0) check(node.markGeometryReady(node.contentRevision()), "leaf becomes render-ready");

        LodNode parent = tree.findNode(new SectionPos(0, 0, 0), 1);
        ParentGpuMeshData parentPayload = ParentGpuMeshData.from(
                ParentLodMeshBuilder.build(parent), parent.contentRevision());
        check(!parentPayload.isEmpty() && parentPayload.indexCount() > 0,
                "aggregated parent produces indexed GLES geometry");
        check(parentPayload.key().equals(new dev.brezze.voxymobile.render.geometry.ParentGeometryKey(
                new SectionPos(0, 0, 0), 1)), "parent payload preserves hierarchy identity");
        check(parent.markGeometryReady(parent.contentRevision()), "parent becomes render-ready");
        var coarse = LodNodeTraversal.collect(tree, 1);
        check(coarse.size() == 1 && coarse.get(0).node() == parent,
                "parent draw selection excludes all eight children");

        tree.put(section(new SectionPos(0, 0, 0), 2));
        check(!parent.hasCurrentGeometry(), "leaf revision invalidates parent");
        var fallback = LodNodeTraversal.collect(tree, 1);
        check(fallback.size() == 7, "stale parent is not selected; seven still-current children remain selectable");
        System.out.println("ParentRenderSelectionSmokeTest: PASS");
    }

    private static VoxelSection section(SectionPos pos, int id) {
        short[] indices = new short[VoxelSection.VOLUME];
        indices[0] = 1;
        return new VoxelSection(pos, new int[]{0, id}, indices);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
