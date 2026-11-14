package dev.brezze.voxymobile.render.culling;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 10 visibility pipeline. Performs conservative 4x4x4-section coarse
 * group rejection, then exact section distance/frustum/occlusion tests.
 * Coordinates passed to FrustumCuller are camera-relative, matching Stage 08.
 * This class is render-thread confined; it owns no GL resources.
 */
public final class SectionVisibilityPipeline {
    public static final int GROUP_EDGE_SECTIONS = 4;
    public static final double DEFAULT_MAX_DISTANCE = 256.0;

    private final SectionOcclusionCuller occlusion;
    private final Map<GroupKey, List<SectionPos>> groups = new HashMap<>();
    private final List<VisibleSection> visible = new ArrayList<>();
    private Stats lastStats = Stats.EMPTY;

    public SectionVisibilityPipeline(SectionOcclusionCuller occlusion) {
        if (occlusion == null) throw new IllegalArgumentException("occlusion culler cannot be null");
        this.occlusion = occlusion;
    }

    /**
     * Builds a near-to-far draw list from sections that currently have GPU meshes.
     * Missing snapshots are ignored. The max distance is measured to section center,
     * while coarse group distance uses the nearest point on the group's AABB.
     */
    public List<VisibleSection> collect(Set<SectionPos> meshSections,
                                        Map<SectionPos, VoxelSection> snapshots,
                                        double cameraX, double cameraY, double cameraZ,
                                        FrustumCuller frustum, double maxDistance) {
        visible.clear();
        // Reuse per-group lists across frames to avoid a fresh list allocation for
        // every visible coarse cell while the camera moves through a stable area.
        for (List<SectionPos> bucket : groups.values()) bucket.clear();
        if (meshSections == null || snapshots == null || frustum == null || maxDistance <= 0.0) {
            groups.clear();
            lastStats = Stats.EMPTY;
            return visible;
        }
        double maxDistanceSq = maxDistance * maxDistance;
        for (SectionPos pos : meshSections) {
            if (!snapshots.containsKey(pos)) continue;
            GroupKey key = new GroupKey(Math.floorDiv(pos.x(), GROUP_EDGE_SECTIONS),
                    Math.floorDiv(pos.y(), GROUP_EDGE_SECTIONS),
                    Math.floorDiv(pos.z(), GROUP_EDGE_SECTIONS));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pos);
        }
        groups.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        int testedGroups = 0;
        int rejectedGroups = 0;
        int testedSections = 0;
        int rejectedDistance = 0;
        int rejectedFrustum = 0;
        int rejectedOcclusion = 0;

        for (Map.Entry<GroupKey, List<SectionPos>> entry : groups.entrySet()) {
            testedGroups++;
            GroupKey group = entry.getKey();
            double minX = group.x * GROUP_EDGE_SECTIONS * 16.0;
            double minY = group.y * GROUP_EDGE_SECTIONS * 16.0;
            double minZ = group.z * GROUP_EDGE_SECTIONS * 16.0;
            double maxX = minX + GROUP_EDGE_SECTIONS * 16.0;
            double maxY = minY + GROUP_EDGE_SECTIONS * 16.0;
            double maxZ = minZ + GROUP_EDGE_SECTIONS * 16.0;
            if (distanceToAabbSquared(cameraX, cameraY, cameraZ, minX, minY, minZ, maxX, maxY, maxZ) > maxDistanceSq
                    || !frustum.isVisible((float) (minX - cameraX), (float) (minY - cameraY), (float) (minZ - cameraZ),
                    (float) (maxX - cameraX), (float) (maxY - cameraY), (float) (maxZ - cameraZ))) {
                rejectedGroups++;
                continue;
            }

            for (SectionPos pos : entry.getValue()) {
                testedSections++;
                double sectionMinX = pos.x() * 16.0;
                double sectionMinY = pos.y() * 16.0;
                double sectionMinZ = pos.z() * 16.0;
                double centerX = sectionMinX + 8.0;
                double centerY = sectionMinY + 8.0;
                double centerZ = sectionMinZ + 8.0;
                double dx = centerX - cameraX;
                double dy = centerY - cameraY;
                double dz = centerZ - cameraZ;
                double distanceSq = dx * dx + dy * dy + dz * dz;
                if (distanceSq > maxDistanceSq) {
                    rejectedDistance++;
                    continue;
                }
                if (!frustum.isVisible((float) (sectionMinX - cameraX), (float) (sectionMinY - cameraY),
                        (float) (sectionMinZ - cameraZ), (float) (sectionMinX + 16.0 - cameraX),
                        (float) (sectionMinY + 16.0 - cameraY), (float) (sectionMinZ + 16.0 - cameraZ))) {
                    rejectedFrustum++;
                    continue;
                }
                VoxelSection section = snapshots.get(pos);
                if (section != null && occlusion.isOccluded(section, snapshots)) {
                    rejectedOcclusion++;
                    continue;
                }
                visible.add(new VisibleSection(pos, distanceSq));
            }
        }
        visible.sort(Comparator.comparingDouble(VisibleSection::distanceSquared));
        lastStats = new Stats(testedGroups, rejectedGroups, testedSections, rejectedDistance,
                rejectedFrustum, rejectedOcclusion, visible.size());
        return visible;
    }

    public Stats lastStats() { return lastStats; }

    public void clear() {
        groups.clear();
        visible.clear();
        lastStats = Stats.EMPTY;
    }

    private static double distanceToAabbSquared(double x, double y, double z,
                                                 double minX, double minY, double minZ,
                                                 double maxX, double maxY, double maxZ) {
        double dx = axisDistance(x, minX, maxX);
        double dy = axisDistance(y, minY, maxY);
        double dz = axisDistance(z, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0.0;
    }

    private record GroupKey(int x, int y, int z) {}

    public record VisibleSection(SectionPos position, double distanceSquared) {}

    public record Stats(int testedGroups, int rejectedGroups, int testedSections,
                        int rejectedDistance, int rejectedFrustum, int rejectedOcclusion,
                        int visibleSections) {
        private static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0, 0);
    }
}
