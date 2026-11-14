package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sparse, incrementally maintained 8-way LOD tree. All public operations are synchronized. */
public final class LodNodeTree {
    public static final int DEFAULT_MAX_LEVEL = 8;
    private final int maxLevel;
    private final Map<SectionPos, LodNode> roots = new HashMap<>();
    private int nodeCount;
    private int leafCount;

    public LodNodeTree() { this(DEFAULT_MAX_LEVEL); }

    public LodNodeTree(int maxLevel) {
        if (maxLevel < 0 || maxLevel > 20) throw new IllegalArgumentException("maxLevel must be 0..20");
        this.maxLevel = maxLevel;
    }

    public int maxLevel() { return maxLevel; }

    /** Inserts or replaces one immutable section snapshot and dirties every ancestor. */
    public synchronized LodNode put(VoxelSection section) {
        if (section == null) throw new IllegalArgumentException("section cannot be null");
        SectionPos pos = section.position();
        SectionPos rootOrigin = alignedOrigin(pos, maxLevel);
        LodNode node = roots.get(rootOrigin);
        if (node == null) {
            node = new LodNode(rootOrigin, maxLevel);
            roots.put(rootOrigin, node);
            nodeCount++;
        }
        List<LodNode> path = new ArrayList<>(maxLevel + 1);
        path.add(node);
        while (node.level() > 0) {
            int childIndex = childIndex(pos, node.level());
            LodNode child = node.child(childIndex);
            if (child == null) {
                SectionPos childOrigin = childOrigin(node.origin(), childIndex, 1 << (node.level() - 1));
                child = new LodNode(childOrigin, node.level() - 1);
                node.setChild(childIndex, child);
                nodeCount++;
            }
            node = child;
            path.add(node);
        }
        boolean wasEmpty = node.source() == null;
        boolean changed = node.source() != section;
        node.setSource(section);
        if (wasEmpty) leafCount++;
        // A source change invalidates all cached aggregate geometry above the leaf.
        if (changed) for (int i = path.size() - 2; i >= 0; i--) path.get(i).markDirty();
        return node;
    }

    /** Removes a leaf and prunes empty ancestors. Returns false when it was not present. */
    public synchronized boolean remove(SectionPos pos) {
        if (pos == null) throw new IllegalArgumentException("position cannot be null");
        SectionPos rootOrigin = alignedOrigin(pos, maxLevel);
        LodNode root = roots.get(rootOrigin);
        if (root == null) return false;
        LodNode[] path = new LodNode[maxLevel + 1];
        int[] childIndices = new int[maxLevel];
        LodNode node = root;
        path[0] = node;
        for (int depth = 0; node.level() > 0; depth++) {
            int index = childIndex(pos, node.level());
            childIndices[depth] = index;
            node = node.child(index);
            if (node == null) return false;
            path[depth + 1] = node;
        }
        if (node.source() == null) return false;
        // Removing the source turns the leaf into an empty node; pruning below removes it.
        node.clearSource();
        leafCount--;
        nodeCount--;
        for (int depth = maxLevel - 1; depth >= 0; depth--) {
            LodNode parent = path[depth];
            LodNode child = path[depth + 1];
            if (child.source() == null && child.populatedChildren() == 0) {
                parent.setChild(childIndices[depth], null);
                if (child.level() > 0) nodeCount--;
            } else {
                parent.markDirty();
            }
        }
        if (root.source() == null && root.populatedChildren() == 0) {
            roots.remove(rootOrigin);
            if (maxLevel > 0) nodeCount--;
        }
        return true;
    }

    public synchronized LodNode findLeaf(SectionPos pos) {
        if (pos == null) return null;
        LodNode node = roots.get(alignedOrigin(pos, maxLevel));
        if (node == null) return null;
        while (node.level() > 0) {
            node = node.child(childIndex(pos, node.level()));
            if (node == null) return null;
        }
        return node.source() == null ? null : node;
    }

    /** Finds the node whose aligned region contains the supplied section position. */
    public synchronized LodNode findNode(SectionPos sectionPos, int level) {
        if (sectionPos == null || level < 0 || level > maxLevel) throw new IllegalArgumentException();
        SectionPos rootOrigin = alignedOrigin(sectionPos, maxLevel);
        LodNode node = roots.get(rootOrigin);
        if (node == null) return null;
        while (node.level() > level) {
            node = node.child(childIndex(sectionPos, node.level()));
            if (node == null) return null;
        }
        return node;
    }

    /**
     * Builds an immutable parent voxel snapshot while holding the tree lock, so chunk updates
     * cannot interleave with recursive aggregation. The returned payload is safe for worker use.
     */
    public synchronized VoxelLodData aggregateNodeData(SectionPos regionPosition, int level) {
        LodNode node = findNode(regionPosition, level);
        return node == null ? null : ParentVoxelAggregator.aggregate(node);
    }

    /** Atomically captures immutable voxel data and its matching content revision for worker jobs. */
    public synchronized NodeDataSnapshot snapshotNodeData(SectionPos regionPosition, int level) {
        LodNode node = findNode(regionPosition, level);
        if (node == null) return null;
        long revision = node.contentRevision();
        LodNodeSnapshot snapshot = copyNode(node);
        return snapshot == null ? null : new NodeDataSnapshot(node.origin(), node.level(), revision, snapshot);
    }

    private static LodNodeSnapshot copyNode(LodNode node) {
        if (node == null) return null;
        LodNodeSnapshot[] children = new LodNodeSnapshot[8];
        for (int i = 0; i < 8; i++) children[i] = copyNode(node.child(i));
        if (node.level() == 0 && node.source() == null) return null;
        boolean populated = node.level() == 0 ? node.source() != null : false;
        for (LodNodeSnapshot child : children) populated |= child != null;
        return populated ? new LodNodeSnapshot(node.origin(), node.level(), node.source(), children) : null;
    }

    /** Marks an asynchronously built mesh current only if its source revision is still current. */
    public synchronized boolean markGeometryReady(SectionPos regionPosition, int level, long builtRevision) {
        LodNode node = findNode(regionPosition, level);
        return node != null && node.markGeometryReady(builtRevision);
    }

    /** Marks cached geometry unavailable after its GPU allocation is evicted. */
    public synchronized boolean invalidateGeometry(SectionPos regionPosition, int level) {
        LodNode node = findNode(regionPosition, level);
        if (node == null) return false;
        node.invalidateGeometry();
        return true;
    }

    public synchronized List<LodNode> rootsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(roots.values()));
    }

    /** Stable structural snapshot for render-thread scheduling; node revisions remain authoritative. */
    public synchronized List<LodNode> nodesSnapshot() {
        List<LodNode> result = new ArrayList<>();
        for (LodNode root : roots.values()) collectNodes(root, result);
        return Collections.unmodifiableList(result);
    }

    private static void collectNodes(LodNode node, List<LodNode> output) {
        if (node == null) return;
        output.add(node);
        for (int i = 0; i < 8; i++) collectNodes(node.child(i), output);
    }

    synchronized List<LodNodeTraversal.Selection> collectInternal(int desiredLevel) {
        if (desiredLevel < 0 || desiredLevel > maxLevel) throw new IllegalArgumentException("desiredLevel out of range");
        return LodNodeTraversal.collectRoots(roots.values(), desiredLevel);
    }

    public synchronized int nodeCount() { return nodeCount; }
    public synchronized int leafCount() { return leafCount; }
    public synchronized boolean isEmpty() { return roots.isEmpty(); }

    public synchronized void clear() {
        roots.clear();
        nodeCount = 0;
        leafCount = 0;
    }

    public static SectionPos alignedOrigin(SectionPos pos, int level) {
        int span = 1 << level;
        return new SectionPos(Math.floorDiv(pos.x(), span) * span,
                Math.floorDiv(pos.y(), span) * span,
                Math.floorDiv(pos.z(), span) * span);
    }

    private static int childIndex(SectionPos pos, int parentLevel) {
        int childSpan = 1 << (parentLevel - 1);
        int x = Math.floorMod(Math.floorDiv(pos.x(), childSpan), 2);
        int y = Math.floorMod(Math.floorDiv(pos.y(), childSpan), 2);
        int z = Math.floorMod(Math.floorDiv(pos.z(), childSpan), 2);
        return x | (y << 1) | (z << 2);
    }

    private static SectionPos childOrigin(SectionPos parent, int index, int childSpan) {
        return new SectionPos(Math.addExact(parent.x(), Math.multiplyExact(index & 1, childSpan)),
                Math.addExact(parent.y(), Math.multiplyExact((index >>> 1) & 1, childSpan)),
                Math.addExact(parent.z(), Math.multiplyExact((index >>> 2) & 1, childSpan)));
    }
}
