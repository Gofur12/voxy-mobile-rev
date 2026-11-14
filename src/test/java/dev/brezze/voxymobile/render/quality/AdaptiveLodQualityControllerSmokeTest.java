package dev.brezze.voxymobile.render.quality;

public final class AdaptiveLodQualityControllerSmokeTest {
    public static void main(String[] args) {
        testInvalidSamplesIgnored();
        testSustainedSlowFramesDowngrade();
        testSustainedFastFramesRecoverGradually();
        testHysteresisPreventsSingleFrameOscillation();
        testReset();
        testStreamingPolicyIntegration();
        System.out.println("AdaptiveLodQualityControllerSmokeTest: PASS (5 cases)");
    }

    private static void testInvalidSamplesIgnored() {
        AdaptiveLodQualityController c = new AdaptiveLodQualityController();
        c.recordFrameTime(Double.NaN);
        c.recordFrameTime(-1);
        c.recordFrameTime(0);
        c.recordFrameTime(1001);
        check(c.sampleCount() == 0 && c.qualityTier() == 0, "invalid samples ignored");
    }

    private static void testSustainedSlowFramesDowngrade() {
        AdaptiveLodQualityController c = new AdaptiveLodQualityController();
        for (int i = 0; i < 80; i++) c.recordFrameTime(30);
        check(c.qualityTier() >= 2, "sustained slow frames reduce quality tier");
        check(c.lodDistanceScale() < 1.0, "coarser LOD selected sooner under load");
        check(c.maxNewBuildsPerFrame() <= 2, "build request budget reduced");
        check(c.maxUploadsPerFrame() == 1, "upload budget reduced");
    }

    private static void testSustainedFastFramesRecoverGradually() {
        AdaptiveLodQualityController c = new AdaptiveLodQualityController();
        for (int i = 0; i < 100; i++) c.recordFrameTime(30);
        int before = c.qualityTier();
        for (int i = 0; i < 160; i++) c.recordFrameTime(7);
        check(c.qualityTier() < before, "sustained headroom restores quality");
        check(c.qualityTier() >= 0, "tier remains bounded");
    }

    private static void testHysteresisPreventsSingleFrameOscillation() {
        AdaptiveLodQualityController c = new AdaptiveLodQualityController();
        c.recordFrameTime(80);
        c.recordFrameTime(8);
        c.recordFrameTime(80);
        check(c.qualityTier() == 0, "isolated spikes do not change tier");
        check(c.maxUploadsPerFrame() == 3, "default upload budget retained");
    }

    private static void testStreamingPolicyIntegration() {
        dev.brezze.voxymobile.render.streaming.AdaptiveLodStreamingController streaming =
                new dev.brezze.voxymobile.render.streaming.AdaptiveLodStreamingController();
        int normal = streaming.selectLod(55.0 * 55.0, -1);
        for (int i = 0; i < 120; i++) streaming.recordFrameTime(35.0);
        int pressured = streaming.selectLod(55.0 * 55.0, -1);
        check(normal == 0 && pressured >= 1, "streaming LOD boundaries adapt under sustained pressure");
        check(streaming.maxNewBuildsPerFrame() < 4, "streaming build request budget adapts");
    }

    private static void testReset() {
        AdaptiveLodQualityController c = new AdaptiveLodQualityController();
        for (int i = 0; i < 100; i++) c.recordFrameTime(40);
        c.reset();
        check(c.qualityTier() == 0 && c.sampleCount() == 0, "reset clears policy state");
        check(c.lodDistanceScale() == 1.0, "reset restores full LOD distances");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
