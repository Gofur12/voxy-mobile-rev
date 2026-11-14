package dev.brezze.voxymobile.render.streaming;

/** Dependency-free Stage 21 visibility-priority checks. */
public final class VisibilityAwareStreamingControllerSmokeTest {
    public static void main(String[] args) {
        VisibilityAwareStreamingController policy = new VisibilityAwareStreamingController();
        double visible = policy.sectionPriority(100.0, true, false);
        double offscreen = policy.sectionPriority(100.0, false, false);
        double enclosed = policy.sectionPriority(100.0, true, true);
        double both = policy.sectionPriority(100.0, false, true);
        if (visible != 100.0) throw new AssertionError("visible work must preserve its base score");
        if (!(visible < offscreen)) throw new AssertionError("visible sections must outrank off-screen sections");
        if (!(visible < enclosed)) throw new AssertionError("non-enclosed sections must outrank enclosed sections");
        if (!(offscreen < both) || !(enclosed < both)) throw new AssertionError("combined penalties must be strongest");
        if (policy.parentPriority(25.0, true) != 25.0) throw new AssertionError("visible parent score changed");
        if (!(policy.parentPriority(25.0, false) > 25.0)) throw new AssertionError("off-screen parent must be deprioritized");
        if (policy.sectionPriority(Double.NaN, true, false) != Double.MAX_VALUE) throw new AssertionError("NaN must be rejected");
        if (policy.sectionPriority(-1.0, true, false) != Double.MAX_VALUE) throw new AssertionError("negative score must be rejected");
        System.out.println("VisibilityAwareStreamingControllerSmokeTest: PASS (8 checks)");
    }
}
