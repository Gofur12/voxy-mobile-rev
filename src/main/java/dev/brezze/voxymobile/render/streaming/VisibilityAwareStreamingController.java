package dev.brezze.voxymobile.render.streaming;

/**
 * CPU-only visibility weighting for streaming queues. Visibility is a priority hint,
 * never a hard rejection: off-screen and conservatively occluded work remains queued
 * so a quick camera turn or newly exposed area does not starve.
 */
public final class VisibilityAwareStreamingController {
    private static final double OFFSCREEN_MULTIPLIER = 2.25;
    private static final double OCCLUDED_MULTIPLIER = 2.75;
    private static final double MAX_SCORE = Double.MAX_VALUE;

    /** Lower scores are scheduled first. Invalid base scores remain safely deprioritized. */
    public double applyVisibility(double basePriority, boolean inFrustum, boolean occluded) {
        if (!Double.isFinite(basePriority) || basePriority < 0.0) return MAX_SCORE;
        double multiplier = 1.0;
        if (!inFrustum) multiplier *= OFFSCREEN_MULTIPLIER;
        if (occluded) multiplier *= OCCLUDED_MULTIPLIER;
        double result = basePriority * multiplier;
        return Double.isFinite(result) && result >= 0.0 ? result : MAX_SCORE;
    }

    public double sectionPriority(double basePriority, boolean inFrustum, boolean fullyEnclosed) {
        return applyVisibility(basePriority, inFrustum, fullyEnclosed);
    }

    public double parentPriority(double basePriority, boolean inFrustum) {
        return applyVisibility(basePriority, inFrustum, false);
    }
}
