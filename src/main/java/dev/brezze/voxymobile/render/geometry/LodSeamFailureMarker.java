package dev.brezze.voxymobile.render.geometry;

/** Immutable description of a seam build failure at its expected world-space boundary. */
public record LodSeamFailureMarker(LodSeamKey key, long revision, LodSeamBounds bounds, String reason) {
    public LodSeamFailureMarker {
        if (key == null || bounds == null) throw new IllegalArgumentException("key and bounds are required");
        if (reason == null || reason.isBlank()) reason = "unknown";
    }
}
