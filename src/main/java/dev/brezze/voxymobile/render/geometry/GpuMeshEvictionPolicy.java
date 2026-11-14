package dev.brezze.voxymobile.render.geometry;

/**
 * Ranks GPU mesh allocations for eviction. Larger scores are evicted first.
 * AABB distance measures how far the region begins, while the level bias protects
 * coarse meshes that cover more terrain and can serve as parent fallbacks.
 */
public final class GpuMeshEvictionPolicy {
    private GpuMeshEvictionPolicy() {}

    public static double score(double distanceToRegionSquared, int lodLevel) {
        if (!Double.isFinite(distanceToRegionSquared) || distanceToRegionSquared < 0.0
                || lodLevel < 0 || lodLevel > 20) return -1.0;
        return distanceToRegionSquared / (1.0 + 0.5 * lodLevel);
    }
}
