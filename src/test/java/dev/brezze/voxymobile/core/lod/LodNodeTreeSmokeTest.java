package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

public final class LodNodeTreeSmokeTest {
    public static void main(String[] args) {
        LodNodeTree tree = new LodNodeTree(2);
        VoxelSection[] sections = new VoxelSection[8];
        for (int i = 0; i < 8; i++) {
            SectionPos pos = new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1);
            sections[i] = section(pos, 1);
            tree.put(sections[i]);
        }
        check(tree.leafCount() == 8, "eight leaves inserted");
        check(tree.findLeaf(new SectionPos(1, 1, 1)) != null, "leaf lookup");
        LodNode root = tree.rootsSnapshot().get(0);
        check(root.level() == 2 && root.populatedChildren() == 1, "aligned root and sparse child hierarchy");
        LodNode parent = tree.findNode(new SectionPos(0, 0, 0), 1);
        check(parent != null && parent.populatedChildren() == 8, "2x2x2 parent populated");

        // A parent mesh is valid only for the revision it was built from.
        long parentRevision = parent.contentRevision();
        check(tree.markGeometryReady(new SectionPos(0, 0, 0), 1, parentRevision), "parent geometry marked ready");
        tree.put(sections[0]);
        check(parent.hasCurrentGeometry(), "re-inserting identical snapshot preserves current parent geometry");
        for (int i = 0; i < 8; i++) {
            LodNode leaf = tree.findLeaf(sections[i].position());
            check(leaf.markGeometryReady(leaf.contentRevision()), "leaf geometry marked ready");
        }
        check(LodNodeTraversal.collect(tree, 0).size() == 8, "fine traversal selects ready leaves");
        check(LodNodeTraversal.collect(tree, 1).size() == 1, "requested parent LOD selects parent");

        // Invalidate one leaf. Parent remains current only until its subtree changes.
        long staleParentRevision = parent.contentRevision();
        VoxelSection replacement = section(new SectionPos(0, 0, 0), 2);
        tree.put(replacement);
        check(!tree.markGeometryReady(new SectionPos(0, 0, 0), 1, staleParentRevision), "stale asynchronous parent result rejected");
        check(!parent.hasCurrentGeometry(), "child update invalidates parent geometry");
        check(LodNodeTraversal.collect(tree, 1).size() == 7, "stale parent is not selected; ready children remain visible");

        // Rebuild parent geometry; it is used as a fallback while a child is missing.
        check(tree.markGeometryReady(new SectionPos(0, 0, 0), 1, parent.contentRevision()), "rebuilt parent accepted");
        check(LodNodeTraversal.collect(tree, 0).size() == 1, "parent fallback replaces incomplete child coverage");
        LodNode changedLeaf = tree.findLeaf(new SectionPos(0, 0, 0));
        check(changedLeaf.markGeometryReady(changedLeaf.contentRevision()), "replacement leaf ready");
        check(LodNodeTraversal.collect(tree, 0).size() == 8, "children replace fallback when coverage is complete");
        check(LodNodeTraversal.collect(tree, 1).size() == 1, "parent selected at requested level");

        check(tree.remove(new SectionPos(0, 0, 0)), "leaf removal");
        check(tree.findLeaf(new SectionPos(0, 0, 0)) == null, "removed leaf absent");
        check(tree.leafCount() == 7, "leaf count after removal");
        check(!tree.remove(new SectionPos(0, 0, 0)), "duplicate removal ignored");
        for (int i = 1; i < 8; i++)
            check(tree.remove(new SectionPos(i & 1, (i >>> 1) & 1, (i >>> 2) & 1)), "remaining leaf removed");
        check(tree.isEmpty() && tree.nodeCount() == 0 && tree.leafCount() == 0, "empty branches fully pruned");

        LodNodeTree negative = new LodNodeTree(2);
        negative.put(section(new SectionPos(-1, -1, -1), 3));
        check(negative.findLeaf(new SectionPos(-1, -1, -1)) != null, "negative coordinate floor alignment");
        check(LodNodeTree.alignedOrigin(new SectionPos(-1, -1, -1), 2).equals(new SectionPos(-4, -4, -4)), "negative root alignment");

        LodNodeTree zero = new LodNodeTree(0);
        zero.put(section(new SectionPos(4, 5, 6), 4));
        check(zero.nodeCount() == 1 && zero.remove(new SectionPos(4, 5, 6)), "level-zero root removal");
        check(zero.nodeCount() == 0 && zero.isEmpty(), "level-zero root count remains valid");
        System.out.println("LodNodeTreeSmokeTest: PASS");
    }

    private static VoxelSection section(SectionPos pos, int blockId) {
        int[] palette = {0, blockId};
        short[] indices = new short[VoxelSection.VOLUME];
        indices[0] = 1;
        return new VoxelSection(pos, palette, indices);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
