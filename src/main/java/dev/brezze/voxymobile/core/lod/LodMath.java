package dev.brezze.voxymobile.core.lod;

/** Deterministic LOD scale helpers; one level doubles the represented world span. */
public final class LodMath {
    private LodMath() {}
    public static int scale(int level) {
        if (level < 0 || level > 30) throw new IllegalArgumentException("level out of range");
        return 1 << level;
    }
    public static int levelForDistance(double distanceBlocks, double baseSectionSize, double targetPixelsPerSection) {
        if (!Double.isFinite(distanceBlocks) || !Double.isFinite(baseSectionSize) || !Double.isFinite(targetPixelsPerSection)
                || distanceBlocks < 0 || baseSectionSize <= 0 || targetPixelsPerSection <= 0) throw new IllegalArgumentException();
        double ratio = Math.max(1.0, distanceBlocks / (baseSectionSize * targetPixelsPerSection));
        return Math.min(20, Math.max(0, (int) Math.floor(Math.log(ratio) / Math.log(2.0))));
    }
}
