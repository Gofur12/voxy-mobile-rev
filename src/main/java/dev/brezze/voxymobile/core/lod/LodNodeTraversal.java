package dev.brezze.voxymobile.core.lod;

import java.util.ArrayList;
import java.util.List;

/** CPU-side parent/child selection with conservative parent-mesh fallback. */
public final class LodNodeTraversal {
    private LodNodeTraversal() {}

    public static List<Selection> collect(LodNodeTree tree, int desiredLevel) {
        if (tree == null) throw new IllegalArgumentException("tree cannot be null");
        return tree.collectInternal(desiredLevel);
    }


    /** Distance-adaptive traversal: selects ready parents at their own spatial scale. */
    public static List<Selection> collectByDistance(LodNodeTree tree, HierarchicalLodSelector selector,
                                                     double cameraX, double cameraY, double cameraZ) {
        if (tree == null || selector == null) throw new IllegalArgumentException("tree and selector are required");
        List<LodNode> liveNodes = tree.nodesSnapshot();
        selector.retainNodes(liveNodes);
        List<Selection> output = new ArrayList<>();
        for (LodNode root : tree.rootsSnapshot())
            selectByDistance(root, selector, cameraX, cameraY, cameraZ, output);
        return output;
    }

    private static boolean selectByDistance(LodNode node, HierarchicalLodSelector selector,
                                            double cameraX, double cameraY, double cameraZ,
                                            List<Selection> output) {
        if (node == null) return true;
        if (node.level() > 0 && selector.prefersNode(node, cameraX, cameraY, cameraZ)
                && node.hasCurrentGeometry()) {
            output.add(new Selection(node, false));
            return true;
        }
        if (node.level() == 0) {
            if (node.hasCurrentGeometry()) output.add(new Selection(node, false));
            return node.hasCurrentGeometry();
        }

        List<Selection> descendants = new ArrayList<>();
        boolean complete = node.populatedChildren() > 0;
        for (int i = 0; i < 8; i++) {
            LodNode child = node.child(i);
            if (child != null && !selectByDistance(child, selector, cameraX, cameraY, cameraZ, descendants))
                complete = false;
        }
        if (complete) {
            output.addAll(descendants);
            return true;
        }
        if (node.hasCurrentGeometry()) {
            output.add(new Selection(node, true));
            return true;
        }
        output.addAll(descendants);
        return false;
    }

    static List<Selection> collectRoots(Iterable<LodNode> roots, int desiredLevel) {
        List<Selection> output = new ArrayList<>();
        for (LodNode root : roots) select(root, desiredLevel, output);
        return output;
    }

    /**
     * Returns true when the selected nodes fully cover this node's populated subtree.
     * If descendants are incomplete, a current parent mesh replaces them to avoid overlap.
     */
    private static boolean select(LodNode node, int desiredLevel, List<Selection> output) {
        if (node == null) return true;
        if (node.level() == desiredLevel && node.hasCurrentGeometry()) {
            output.add(new Selection(node, false));
            return true;
        }
        if (node.level() < desiredLevel && node.hasCurrentGeometry()) {
            output.add(new Selection(node, true)); // finer geometry is a safe fallback for a missing coarse mesh
            return true;
        }

        List<Selection> descendants = new ArrayList<>();
        boolean complete = node.populatedChildren() > 0;
        for (int i = 0; i < 8; i++) {
            LodNode child = node.child(i);
            if (child != null && !select(child, desiredLevel, descendants)) complete = false;
        }
        if (complete) {
            output.addAll(descendants);
            return true;
        }
        if (node.hasCurrentGeometry()) {
            output.add(new Selection(node, node.level() != desiredLevel));
            return true;
        }
        // No parent fallback exists yet. Keep only available descendants; never emit stale geometry.
        output.addAll(descendants);
        return false;
    }

    public record Selection(LodNode node, boolean fallback) {}
}
