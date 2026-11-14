package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stage 18 contract for distance bands, AABB distance, hysteresis and non-overlapping selection. */
public final class HierarchicalLodSelectionSmokeTest {
    public static void main(String[] args) {
        check(HierarchicalLodSelector.entryDistance(1) == 56.0, "level 1 entry distance");
        check(HierarchicalLodSelector.entryDistance(2) == 112.0, "level 2 entry distance");
        check(HierarchicalLodSelector.entryDistance(3) == 192.0, "level 3 entry distance");
        check(HierarchicalLodSelector.entryDistance(6) == 768.0, "level 6 entry distance");

        LodNodeTree tree = new LodNodeTree(2);
        for (int i = 0; i < 8; i++) {
            SectionPos pos = new SectionPos((i & 1) * 2, ((i >>> 1) & 1) * 2, ((i >>> 2) & 1) * 2);
            tree.put(section(pos));
        }
        for (LodNode node : tree.nodesSnapshot())
            check(node.markGeometryReady(node.contentRevision()), "all node levels marked ready");

        HierarchicalLodSelector selector = new HierarchicalLodSelector();
        List<LodNodeTraversal.Selection> near = LodNodeTraversal.collectByDistance(tree, selector, 8, 8, 8);
        check(near.size() == 8, "near camera selects fine leaves");
        check(near.stream().allMatch(s -> s.node().level() == 0), "near selection contains only leaves");

        List<LodNodeTraversal.Selection> medium = LodNodeTraversal.collectByDistance(tree, selector, 100, 8, 8);
        long mediumParents = medium.stream().filter(s -> s.node().level() == 1).count();
        long mediumLeaves = medium.stream().filter(s -> s.node().level() == 0).count();
        check(mediumParents == 4 && mediumLeaves == 4, "medium camera mixes parent and leaf detail by region");
        assertNoAncestorOverlap(medium);

        List<LodNodeTraversal.Selection> far = LodNodeTraversal.collectByDistance(tree, selector, 4000, 4000, 4000);
        check(far.size() == 1 && far.get(0).node().level() == 2, "far camera selects coarse root parent");
        assertNoAncestorOverlap(far);

        LodNode levelOne = tree.findNode(new SectionPos(0, 0, 0), 1);
        HierarchicalLodSelector hysteresis = new HierarchicalLodSelector();
        check(hysteresis.prefersNode(levelOne, 92, 8, 8), "outside entry boundary selects parent");
        check(hysteresis.prefersNode(levelOne, 82, 8, 8), "hysteresis retains selected parent");
        check(!hysteresis.prefersNode(levelOne, 80, 8, 8), "hysteresis exits when returning inside boundary");

        selector.retainNodes(List.of());
        check(selector.trackedNodeCount() == 0, "removed nodes release hysteresis state");
        System.out.println("HierarchicalLodSelectionSmokeTest: PASS (10 assertions)");
    }

    private static void assertNoAncestorOverlap(List<LodNodeTraversal.Selection> selections) {
        Set<String> keys = new HashSet<>();
        for (LodNodeTraversal.Selection selection : selections) {
            String key = selection.node().origin() + "/" + selection.node().level();
            check(keys.add(key), "selection has no duplicate node");
            for (LodNodeTraversal.Selection other : selections) {
                if (selection == other) continue;
                if (isAncestor(selection.node(), other.node()) || isAncestor(other.node(), selection.node()))
                    throw new AssertionError("ancestor and descendant selected together");
            }
        }
    }

    private static boolean isAncestor(LodNode ancestor, LodNode child) {
        if (ancestor.level() <= child.level()) return false;
        int span = ancestor.spanSections();
        SectionPos a = ancestor.origin(), c = child.origin();
        return c.x() >= a.x() && c.y() >= a.y() && c.z() >= a.z()
                && c.x() < a.x() + span && c.y() < a.y() + span && c.z() < a.z() + span;
    }

    private static VoxelSection section(SectionPos pos) {
        short[] indices = new short[VoxelSection.VOLUME];
        indices[0] = 1;
        return new VoxelSection(pos, new int[]{0, 1}, indices);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
