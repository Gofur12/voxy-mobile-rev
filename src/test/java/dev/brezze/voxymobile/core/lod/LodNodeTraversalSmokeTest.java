package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

public final class LodNodeTraversalSmokeTest {
    public static void main(String[] args) {
        LodNodeTree tree = new LodNodeTree(1);
        for (int i = 0; i < 8; i++) {
            SectionPos pos = new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1);
            tree.put(new VoxelSection(pos, new int[]{0, 1}, filledIndices()));
        }
        LodNode parent = tree.findNode(new SectionPos(0, 0, 0), 1);
        check(LodNodeTraversal.collect(tree, 0).isEmpty(), "unbuilt tree emits no stale mesh");
        check(parent.markGeometryReady(parent.contentRevision()), "parent fallback geometry ready");
        check(LodNodeTraversal.collect(tree, 0).size() == 1, "parent selected when children are not ready");
        for (int i = 0; i < 8; i++) {
            LodNode leaf = tree.findLeaf(new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1));
            leaf.markGeometryReady(leaf.contentRevision());
        }
        check(LodNodeTraversal.collect(tree, 0).size() == 8, "children replace parent when complete");
        check(LodNodeTraversal.collectByDistance(tree, new HierarchicalLodSelector(), 1000, 1000, 1000, 64).isEmpty(),
                "independent radius excludes distant root");
        check(LodNodeTraversal.collectByDistance(tree, new HierarchicalLodSelector(), 1000, 1000, 1000, 4096).size() == 1,
                "larger independent radius includes root hierarchy");
        System.out.println("LodNodeTraversalSmokeTest: PASS");
    }

    private static short[] filledIndices() {
        short[] data = new short[VoxelSection.VOLUME];
        data[0] = 1;
        return data;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
