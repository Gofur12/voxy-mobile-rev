package dev.brezze.voxymobile.render.geometry;

/** CPU-only seam-to-terrain boundary gap detector using point-to-polyline distance. */
public final class LodSeamCrackDetector {
    private LodSeamCrackDetector() {}

    public record Report(int seamSamples, int matchedSamples, int crackedSamples,
                         double maxGap, double meanGap, double tolerance) {
        public boolean hasCracks() { return crackedSamples > 0; }
    }

    /**
     * Compares XYZ seam boundary samples against an XYZ terrain boundary polyline.
     * terrainClosed=true also tests the final-to-first terrain segment.
     */
    public static Report inspect(float[] seamXYZ, float[] terrainXYZ,
                                 boolean terrainClosed, double tolerance) {
        validate(seamXYZ, "seam boundary");
        validate(terrainXYZ, "terrain boundary");
        if (seamXYZ.length < 3) throw new IllegalArgumentException("at least one seam sample required");
        if (terrainXYZ.length < 6) throw new IllegalArgumentException("at least two terrain boundary points required");
        if (!Double.isFinite(tolerance) || tolerance < 0)
            throw new IllegalArgumentException("tolerance must be finite and non-negative");

        int seamCount = seamXYZ.length / 3;
        int terrainCount = terrainXYZ.length / 3;
        int matched = 0;
        double maxGap = 0.0;
        double sumGap = 0.0;
        for (int i = 0; i < seamXYZ.length; i += 3) {
            double bestSquared = Double.POSITIVE_INFINITY;
            for (int j = 0; j < terrainCount - 1; j++)
                bestSquared = Math.min(bestSquared, pointSegmentDistanceSquared(seamXYZ, i, terrainXYZ, j * 3, j * 3 + 3));
            if (terrainClosed)
                bestSquared = Math.min(bestSquared, pointSegmentDistanceSquared(seamXYZ, i, terrainXYZ, (terrainCount - 1) * 3, 0));
            double gap = Math.sqrt(bestSquared);
            maxGap = Math.max(maxGap, gap);
            sumGap += gap;
            if (gap <= tolerance) matched++;
        }
        return new Report(seamCount, matched, seamCount - matched, maxGap, sumGap / seamCount, tolerance);
    }

    private static double pointSegmentDistanceSquared(float[] p, int pi, float[] line, int ai, int bi) {
        double px = p[pi], py = p[pi + 1], pz = p[pi + 2];
        double ax = line[ai], ay = line[ai + 1], az = line[ai + 2];
        double dx = line[bi] - ax, dy = line[bi + 1] - ay, dz = line[bi + 2] - az;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        double t = lengthSquared == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double ex = px - (ax + t * dx), ey = py - (ay + t * dy), ez = pz - (az + t * dz);
        return ex * ex + ey * ey + ez * ez;
    }

    private static void validate(float[] values, String name) {
        if (values == null || values.length % 3 != 0)
            throw new IllegalArgumentException(name + " must be packed XYZ triples");
        for (float value : values) if (!Float.isFinite(value))
            throw new IllegalArgumentException(name + " contains non-finite coordinate");
    }
}
