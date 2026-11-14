package dev.brezze.voxymobile.core.lod;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateful distance policy for hierarchical parent nodes. Distances are measured to the
 * closest point of each node's world-space AABB, not its center, so large parent regions
 * transition smoothly as the camera approaches their edge.
 */
public final class HierarchicalLodSelector {
    private static final double[] LEVEL_ENTRY_DISTANCE = {0.0, 56.0, 112.0, 192.0, 320.0, 512.0, 768.0};
    private static final double HYSTERESIS_RATIO = 0.12;
    private final Map<NodeKey, Boolean> selected = new HashMap<>();

    /** Returns whether this parent level is the preferred detail for its current distance. */
    public boolean prefersNode(LodNode node, double cameraX, double cameraY, double cameraZ) {
        if (node == null || node.level() <= 0) return false;
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ))
            throw new IllegalArgumentException("camera coordinates must be finite");

        double span = node.spanSections() * 16.0;
        double minX = node.origin().x() * 16.0;
        double minY = node.origin().y() * 16.0;
        double minZ = node.origin().z() * 16.0;
        double dx = distanceToInterval(cameraX, minX, minX + span);
        double dy = distanceToInterval(cameraY, minY, minY + span);
        double dz = distanceToInterval(cameraZ, minZ, minZ + span);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double threshold = entryDistance(node.level());
        double hysteresis = Math.max(4.0, threshold * HYSTERESIS_RATIO);
        NodeKey key = new NodeKey(node.origin(), node.level());
        Boolean wasSelected = selected.get(key);
        boolean nowSelected;
        if (wasSelected == null) nowSelected = distance >= threshold;
        else nowSelected = wasSelected ? distance >= threshold - hysteresis
                                       : distance >= threshold + hysteresis;
        selected.put(key, nowSelected);
        return nowSelected;
    }

    /**
     * Whether a missing parent mesh is currently useful to build for the active distance band.
     * Ready meshes are never rebuilt by this policy; non-preferred missing levels are deferred
     * until the camera enters their hierarchy band.
     */
    public boolean shouldBuildParentGeometry(LodNode node, double cameraX, double cameraY, double cameraZ) {
        return node != null && node.level() > 0 && !node.hasCurrentGeometry()
                && prefersNode(node, cameraX, cameraY, cameraZ);
    }

    /** Drops hysteresis state for nodes no longer present in the sparse tree. */
    public void retainNodes(List<LodNode> liveNodes) {
        Set<NodeKey> live = new HashSet<>();
        for (LodNode node : liveNodes) if (node.level() > 0) live.add(new NodeKey(node.origin(), node.level()));
        selected.keySet().retainAll(live);
    }

    public void clear() { selected.clear(); }
    public int trackedNodeCount() { return selected.size(); }

    public static double entryDistance(int level) {
        if (level < 1 || level > 20) throw new IllegalArgumentException("parent level must be 1..20");
        if (level < LEVEL_ENTRY_DISTANCE.length) return LEVEL_ENTRY_DISTANCE[level];
        return LEVEL_ENTRY_DISTANCE[LEVEL_ENTRY_DISTANCE.length - 1]
                * Math.pow(1.6, level - (LEVEL_ENTRY_DISTANCE.length - 1));
    }

    private static double distanceToInterval(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0.0;
    }

    private record NodeKey(SectionPos origin, int level) {}
}
