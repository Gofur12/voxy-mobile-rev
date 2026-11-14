package dev.brezze.voxymobile.core.lod;

/** Distance-based LOD selection with hysteresis to prevent camera-boundary thrashing. */
public final class LodSelector {
    private static final double[] BOUNDARIES = {56.0, 112.0, 192.0};
    private static final double HYSTERESIS = 8.0;

    private LodSelector() {}

    /** @param distanceSquared squared distance from camera to section center
     *  @param previousLod previous selection, or -1 when no selection exists
     */
    public static int select(double distanceSquared, int previousLod) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0)
            throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
        if (previousLod < -1 || previousLod > 3)
            throw new IllegalArgumentException("previousLod must be -1..3");

        double distance = Math.sqrt(distanceSquared);
        if (previousLod == 0 && distance < BOUNDARIES[0] + HYSTERESIS) return 0;
        if (previousLod == 1 && distance >= BOUNDARIES[0] - HYSTERESIS
                && distance < BOUNDARIES[1] + HYSTERESIS) return 1;
        if (previousLod == 2 && distance >= BOUNDARIES[1] - HYSTERESIS
                && distance < BOUNDARIES[2] + HYSTERESIS) return 2;
        if (previousLod == 3 && distance >= BOUNDARIES[2] - HYSTERESIS) return 3;

        if (distance >= BOUNDARIES[2]) return 3;
        if (distance >= BOUNDARIES[1]) return 2;
        if (distance >= BOUNDARIES[0]) return 1;
        return 0;
    }
}
