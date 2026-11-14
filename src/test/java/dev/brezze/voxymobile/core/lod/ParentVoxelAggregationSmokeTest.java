package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

public final class ParentVoxelAggregationSmokeTest {
    public static void main(String[] args) {
        testEightChildPlacementAndReduction();
        testSparseChildrenTreatMissingAsAir();
        testRecursiveAggregation();
        testNegativeWorldAlignment();
        testParentGreedyMesh();
        testTreeSnapshotAggregation();
        System.out.println("ParentVoxelAggregationSmokeTest: PASS (6 cases)");
    }

    private static void testEightChildPlacementAndReduction() {
        LodNode root = rootAt(new SectionPos(0, 0, 0), 1);
        for (int i = 0; i < 8; i++) root.setChild(i, leaf(new SectionPos((i & 1) * 1, ((i >>> 1) & 1), ((i >>> 2) & 1)), 7));
        VoxelLodData data = ParentVoxelAggregator.aggregate(root);
        check(data != null && data.level() == 1, "parent level");
        check(data.blockId(0, 0, 0) == 7, "lower corner material");
        check(data.blockId(15, 15, 15) == 7, "upper corner material");
        check(data.nonAirCount() == 4096, "full parent occupancy");
    }

    private static void testSparseChildrenTreatMissingAsAir() {
        LodNode root = rootAt(new SectionPos(0, 0, 0), 1);
        root.setChild(0, leaf(new SectionPos(0, 0, 0), 9));
        VoxelLodData data = ParentVoxelAggregator.aggregate(root);
        check(data.nonAirCount() == 512, "one child fills one eighth parent volume");
        check(data.blockId(0, 0, 0) == 9, "present child preserved");
        check(data.blockId(15, 0, 0) == 0, "missing child is air");
    }

    private static void testRecursiveAggregation() {
        LodNode root = rootAt(new SectionPos(0, 0, 0), 2);
        LodNode mid = new LodNode(new SectionPos(0, 0, 0), 1);
        mid.setChild(0, leaf(new SectionPos(0, 0, 0), 3));
        root.setChild(0, mid);
        VoxelLodData data = ParentVoxelAggregator.aggregate(root);
        check(data != null && data.level() == 2, "recursive level");
        check(data.blockId(0, 0, 0) == 3, "recursive material survives");
        check(data.nonAirCount() == 64, "recursive sparse coverage");
    }

    private static void testNegativeWorldAlignment() {
        SectionPos leafPos = new SectionPos(-1, -1, -1);
        LodNode root = new LodNode(new SectionPos(-2, -2, -2), 1);
        root.setChild(7, leaf(leafPos, 11));
        VoxelLodData data = ParentVoxelAggregator.aggregate(root);
        check(data.origin().equals(new SectionPos(-2, -2, -2)), "negative parent origin");
        check(data.blockId(8, 8, 8) == 11, "negative child maps to high octant");
    }

    private static void testParentGreedyMesh() {
        LodNode root = rootAt(new SectionPos(0, 0, 0), 1);
        root.setChild(0, leaf(new SectionPos(0, 0, 0), 5));
        ParentLodMeshData mesh = ParentLodMeshBuilder.build(root);
        check(mesh != null && mesh.level() == 1, "parent mesh generated");
        check(!mesh.isEmpty(), "parent mesh has faces");
        check(mesh.quads().size() == 6, "solid cuboid greedily merges to six faces");
        check(mesh.cellBlockSize() == 2, "parent voxel scale");
    }

    private static void testTreeSnapshotAggregation() {
        LodNodeTree tree = new LodNodeTree(1);
        tree.put(new VoxelSection(new SectionPos(0, 0, 0), new int[]{0, 13}, solidIndices()));
        VoxelLodData data = tree.aggregateNodeData(new SectionPos(0, 0, 0), 1);
        check(data != null && data.nonAirCount() == 512, "tree synchronized aggregate snapshot");
        check(ParentLodMeshBuilder.build(data) != null, "snapshot can be meshed independently");
    }

    private static short[] solidIndices() {
        short[] indices = new short[VoxelSection.VOLUME];
        java.util.Arrays.fill(indices, (short) 1);
        return indices;
    }

    private static LodNode rootAt(SectionPos pos, int level) { return new LodNode(pos, level); }
    private static LodNode leaf(SectionPos pos, int blockId) {
        int[] palette = {0, blockId};
        short[] indices = new short[VoxelSection.VOLUME];
        java.util.Arrays.fill(indices, (short) 1);
        LodNode node = new LodNode(pos, 0);
        node.setSource(new VoxelSection(pos, palette, indices));
        return node;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
