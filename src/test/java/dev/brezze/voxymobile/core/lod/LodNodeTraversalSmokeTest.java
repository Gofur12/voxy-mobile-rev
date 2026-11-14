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
        testGpuReadyFallback();
        testBoundedHierarchyQuery();
        System.out.println("LodNodeTraversalSmokeTest: PASS");
    }

    private static void testGpuReadyFallback() {
        LodNodeTree tree = new LodNodeTree(1);
        for (int i = 0; i < 8; i++) {
            SectionPos pos = new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1);
            tree.put(new VoxelSection(pos, new int[]{0, 1}, filledIndices()));
        }
        LodNode parent = tree.findNode(new SectionPos(0, 0, 0), 1);
        parent.markGeometryReady(parent.contentRevision());
        for (int i = 0; i < 8; i++) {
            LodNode leaf = tree.findLeaf(new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1));
            leaf.markGeometryReady(leaf.contentRevision());
        }
        var selected = LodNodeTraversal.collectByDistance(tree, new HierarchicalLodSelector(),
                8.0, 8.0, 8.0, 4096.0, node -> node.level() > 0 && node.hasCurrentGeometry());
        check(selected.size() == 1 && selected.get(0).node() == parent && selected.get(0).fallback(),
                "renderable parent replaces logically-ready children without GPU meshes");
    }

    private static void testBoundedHierarchyQuery() {
        LodNodeTree tree = new LodNodeTree(3);
        for (int x = 0; x < 8; x++)
            tree.put(new VoxelSection(new SectionPos(x * 16, 0, 0), new int[]{0, 1}, filledIndices()));
        int all = tree.nodesSnapshot().size();
        var near = tree.nodesWithinDistance(8.0, 8.0, 8.0, 300.0);
        check(!near.isEmpty(), "nearby hierarchy nodes returned");
        check(near.size() < all, "distant branches pruned");
        check(tree.nodesWithinDistance(8.0, 8.0, 8.0, 32.0).size() < near.size(),
                "smaller radius returns fewer nodes");
        check(tree.rootsWithinDistance(8.0, 8.0, 8.0, 32.0).size()
                <= tree.rootsSnapshot().size(), "root query never expands beyond all roots");
        check(LodNodeTraversal.collectByDistance(tree, new HierarchicalLodSelector(),
                8.0, 8.0, 8.0, 32.0).stream().allMatch(selection ->
                distanceToBounds(selection.node(), 8.0, 8.0, 8.0) <= 32.0),
                "draw traversal emits only nodes inside the LOD radius");
    }

    private static double distanceToBounds(LodNode node, double x, double y, double z) {
        double minX = node.origin().x() * 16.0, minY = node.origin().y() * 16.0, minZ = node.origin().z() * 16.0;
        double span = node.spanSections() * 16.0;
        double dx = x < minX ? minX - x : x > minX + span ? x - minX - span : 0.0;
        double dy = y < minY ? minY - y : y > minY + span ? y - minY - span : 0.0;
        double dz = z < minZ ? minZ - z : z > minZ + span ? z - minZ - span : 0.0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
