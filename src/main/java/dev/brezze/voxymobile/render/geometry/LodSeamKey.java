package dev.brezze.voxymobile.render.geometry;

/** Stable identity for a seam between two distinct active LOD region IDs. */
public record LodSeamKey(long firstRegionId, long secondRegionId) {
    public LodSeamKey {
        if (firstRegionId == secondRegionId) throw new IllegalArgumentException("seam regions must differ");
        if (firstRegionId > secondRegionId) {
            long swap = firstRegionId;
            firstRegionId = secondRegionId;
            secondRegionId = swap;
        }
    }
}
