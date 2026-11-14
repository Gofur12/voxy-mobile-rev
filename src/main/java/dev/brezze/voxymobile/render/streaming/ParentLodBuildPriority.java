package dev.brezze.voxymobile.render.streaming;

/**
 * Gives coarse parent nodes a bounded scheduling advantage so distant terrain gets
 * a usable low-detail representation before expensive nearby refinement.
 */
public final class ParentLodBuildPriority {
    private static final double COARSE_LEVEL_BONUS = 0.35;
    private static final int MAX_LEVEL = 20;

    private ParentLodBuildPriority() {}

    /** Lower scores run first; level 0 remains unchanged and higher levels gain a capped bias. */
    public static double apply(double basePriority, int level) {
        if (!Double.isFinite(basePriority) || basePriority < 0.0 || level < 0 || level > MAX_LEVEL)
            return Double.MAX_VALUE;
        return basePriority / (1.0 + COARSE_LEVEL_BONUS * level);
    }
}
