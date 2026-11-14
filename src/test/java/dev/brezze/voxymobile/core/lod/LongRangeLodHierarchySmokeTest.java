package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.List;

/** Stage 68: verifies sparse 4096-block parent tiles participate in far-distance selection. */
public final class LongRangeLodHierarchySmokeTest {
    public static void main(String[] args) {
        LodNodeTree tree = new LodNodeTree();
        check(tree.maxLevel() == 8, "default hierarchy reaches level 8");
        check(tree.findNode(new SectionPos(0, 0, 0), 8) == null, "empty tree has no synthetic root");

        tree.put(section(new SectionPos(0, 0, 0)));
        tree.put(section(new SectionPos(255, 255, 255)));
        LodNode root = tree.findNode(new SectionPos(255, 255, 255), 8);
        check(root != null, "distant corner sections share a level-8 root");
        check(root.spanSections() == 256, "level-8 parent spans 256 sections");
        check(root.spanSections() * 16 == 4096, "level-8 parent spans 4096 blocks");
        check(LodNodeTree.alignedOrigin(new SectionPos(-1, -1, -1), 8)
                .equals(new SectionPos(-256, -256, -256)), "negative coordinates align with floor division");

        check(root.markGeometryReady(root.contentRevision()), "far parent geometry marked ready");
        List<LodNodeTraversal.Selection> far = LodNodeTraversal.collectByDistance(
                tree, new HierarchicalLodSelector(), 8192.0, 2048.0, 2048.0, 8192.0);
        check(far.size() == 1 && far.get(0).node().level() == 8,
                "level-8 parent is selected at 4096-block distance inside 8192 radius");

        List<LodNodeTraversal.Selection> outside = LodNodeTraversal.collectByDistance(
                tree, new HierarchicalLodSelector(), 8192.0, 2048.0, 2048.0, 2048.0);
        check(outside.isEmpty(), "independent radius still excludes distant parent");

        check(HierarchicalLodSelector.entryDistance(8) > HierarchicalLodSelector.entryDistance(7),
                "coarser level has a farther entry threshold");
        System.out.println("LongRangeLodHierarchySmokeTest: PASS");
    }

    private static VoxelSection section(SectionPos pos) {
        short[] blocks = new short[VoxelSection.VOLUME];
        blocks[0] = 1;
        return new VoxelSection(pos, new int[]{0, 1}, blocks);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
