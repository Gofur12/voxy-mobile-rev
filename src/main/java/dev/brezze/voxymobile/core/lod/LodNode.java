package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

/** One node in an 8-way hierarchy. Level 0 is a 16^3 section; each parent doubles its span. */
public final class LodNode {
    private final SectionPos origin;
    private final int level;
    private final LodNode[] children = new LodNode[8];
    private VoxelSection source;
    private boolean dirty = true;
    private long contentRevision;
    private long geometryRevision = -1L;

    public LodNode(SectionPos origin, int level) {
        if (origin == null || level < 0 || level > 20) throw new IllegalArgumentException("invalid node origin/level");
        this.origin = origin;
        this.level = level;
    }

    public SectionPos origin() { return origin; }
    public int level() { return level; }
    public int spanSections() { return 1 << level; }
    public boolean isLeaf() { return populatedChildren() == 0; }
    public boolean isDirty() { return dirty; }
    public long contentRevision() { return contentRevision; }
    public long geometryRevision() { return geometryRevision; }
    public boolean hasCurrentGeometry() { return !dirty && geometryRevision == contentRevision; }

    /** Source snapshots belong to level 0 nodes. */
    public void setSource(VoxelSection section) {
        if (level != 0) throw new IllegalStateException("source snapshots belong to level 0");
        if (section == null || !section.position().equals(origin)) throw new IllegalArgumentException("section position mismatch");
        if (source == section) return;
        source = section;
        markDirty();
    }

    public VoxelSection source() { return source; }

    void clearSource() {
        if (level != 0) throw new IllegalStateException("only leaves hold source snapshots");
        if (source != null) { source = null; markDirty(); }
    }
    public LodNode child(int index) { checkChildIndex(index); return children[index]; }

    public void setChild(int index, LodNode child) {
        checkChildIndex(index);
        if (level == 0) throw new IllegalStateException("level 0 nodes cannot have children");
        if (child != null) {
            if (child.level != level - 1) throw new IllegalArgumentException("child level mismatch");
            SectionPos expected = childOrigin(origin, index, 1 << (level - 1));
            if (!expected.equals(child.origin)) throw new IllegalArgumentException("child origin mismatch: expected " + expected + " got " + child.origin);
        }
        if (children[index] == child) return;
        children[index] = child;
        markDirty();
    }

    /** Marks this node's aggregate and any cached geometry stale. */
    public void markDirty() {
        dirty = true;
        geometryRevision = -1L;
        contentRevision++;
    }

    /** Compatibility helper; callers should mark every ancestor through LodNodeTree. */
    public void markDirtyUpward() { markDirty(); }

    /** Marks geometry valid only for the exact content revision that was meshed. */
    public boolean markGeometryReady(long builtRevision) {
        if (builtRevision != contentRevision) return false;
        dirty = false;
        geometryRevision = builtRevision;
        return true;
    }

    public void invalidateGeometry() {
        dirty = true;
        geometryRevision = -1L;
    }

    public int populatedChildren() {
        int count = 0;
        for (LodNode child : children) if (child != null) count++;
        return count;
    }

    private static SectionPos childOrigin(SectionPos parent, int index, int childSpan) {
        int x = Math.addExact(parent.x(), Math.multiplyExact(index & 1, childSpan));
        int y = Math.addExact(parent.y(), Math.multiplyExact((index >>> 1) & 1, childSpan));
        int z = Math.addExact(parent.z(), Math.multiplyExact((index >>> 2) & 1, childSpan));
        return new SectionPos(x, y, z);
    }

    private static void checkChildIndex(int index) {
        if (index < 0 || index >= 8) throw new IndexOutOfBoundsException("child index " + index);
    }
}
