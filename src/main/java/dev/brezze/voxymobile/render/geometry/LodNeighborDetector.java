package dev.brezze.voxymobile.render.geometry;

/** CPU-side detector for horizontally adjacent, axis-aligned terrain LOD regions. */
public final class LodNeighborDetector {
    private static final double EPSILON = 1.0e-5;
    private LodNeighborDetector() {}

    public enum Edge { EAST_WEST, WEST_EAST, SOUTH_NORTH, NORTH_SOUTH }

    public static final class Region {
        public final double minX, minZ, maxX, maxZ;
        public final double minY, maxY;
        public final boolean hasVerticalBounds;
        public final int lod;

        /** Legacy horizontal-only region. */
        public Region(double minX, double minZ, double maxX, double maxZ, int lod) {
            this(minX, 0.0, minZ, maxX, 0.0, maxZ, lod, false);
        }

        /** 3D-bounded region; vertical overlap is required before horizontal adjacency is accepted. */
        public Region(double minX, double minY, double minZ,
                      double maxX, double maxY, double maxZ, int lod) {
            this(minX, minY, minZ, maxX, maxY, maxZ, lod, true);
        }

        private Region(double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ, int lod, boolean boundedY) {
            if (!finite(minX) || !finite(minZ) || !finite(maxX) || !finite(maxZ)
                    || (boundedY && (!finite(minY) || !finite(maxY) || maxY <= minY))
                    || maxX <= minX || maxZ <= minZ || lod < 0)
                throw new IllegalArgumentException("invalid region bounds or LOD");
            this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ;
            this.minY = minY; this.maxY = maxY; this.hasVerticalBounds = boundedY; this.lod = lod;
        }
    }

    public static final class NeighborPair {
        public final Edge edge;
        public final double plane;
        public final double overlapMin;
        public final double overlapMax;
        public final int lodDelta;
        private NeighborPair(Edge edge, double plane, double overlapMin, double overlapMax, int lodDelta) {
            this.edge = edge; this.plane = plane; this.overlapMin = overlapMin;
            this.overlapMax = overlapMax; this.lodDelta = lodDelta;
        }
        public boolean requiresSeam() { return lodDelta != 0; }
    }

    /** Returns a shared-edge description only when the regions touch with positive overlap. */
    public static NeighborPair detect(Region a, Region b) {
        if (a == null || b == null) throw new IllegalArgumentException("regions must not be null");
        if (a.hasVerticalBounds && b.hasVerticalBounds
                && Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY) <= EPSILON) return null;
        double overlapZMin = Math.max(a.minZ, b.minZ);
        double overlapZMax = Math.min(a.maxZ, b.maxZ);
        double overlapXMin = Math.max(a.minX, b.minX);
        double overlapXMax = Math.min(a.maxX, b.maxX);
        int delta = Math.abs(a.lod - b.lod);
        if (Math.abs(a.maxX - b.minX) <= EPSILON && overlapZMax - overlapZMin > EPSILON)
            return new NeighborPair(Edge.EAST_WEST, a.maxX, overlapZMin, overlapZMax, delta);
        if (Math.abs(a.minX - b.maxX) <= EPSILON && overlapZMax - overlapZMin > EPSILON)
            return new NeighborPair(Edge.WEST_EAST, a.minX, overlapZMin, overlapZMax, delta);
        if (Math.abs(a.maxZ - b.minZ) <= EPSILON && overlapXMax - overlapXMin > EPSILON)
            return new NeighborPair(Edge.SOUTH_NORTH, a.maxZ, overlapXMin, overlapXMax, delta);
        if (Math.abs(a.minZ - b.maxZ) <= EPSILON && overlapXMax - overlapXMin > EPSILON)
            return new NeighborPair(Edge.NORTH_SOUTH, a.minZ, overlapXMin, overlapXMax, delta);
        return null;
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
