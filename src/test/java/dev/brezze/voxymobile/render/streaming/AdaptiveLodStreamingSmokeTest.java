package dev.brezze.voxymobile.render.streaming;

/** Dependency-free Stage 11 checks; executable with a plain Java 17 runtime. */
public final class AdaptiveLodStreamingSmokeTest {
    public static void main(String[] args) {
        AdaptiveLodStreamingController controller = new AdaptiveLodStreamingController();
        controller.update(0, 0, 0, 1_000_000_000L);
        int stationaryLod = controller.selectLod(100.0 * 100.0, -1);
        if (stationaryLod != 1) throw new AssertionError("stationary LOD boundary changed unexpectedly");
        if (controller.uploadsPerFrame(8) != 3) throw new AssertionError("stationary upload budget should be 3");
        if (controller.maxParentBuildsPerFrame() != 2) throw new AssertionError("stationary parent build budget should be 2");

        controller.update(10, 0, 0, 1_250_000_000L);
        if (controller.speedBlocksPerSecond() <= 0.0) throw new AssertionError("camera speed was not measured");
        int movingLod = controller.selectLod(100.0 * 100.0, -1);
        if (movingLod < stationaryLod) throw new AssertionError("fast movement must not request finer LOD");
        if (controller.uploadsPerFrame(8) != 1) throw new AssertionError("fast movement upload budget should be 1");
        if (controller.maxParentBuildsPerFrame() != 1) throw new AssertionError("fast movement parent build budget should be 1");

        double predicted = controller.predictedDistanceSquared(20, 0, 0, 10, 0, 0);
        double current = 10.0 * 10.0;
        if (predicted >= current) throw new AssertionError("forward predicted position should prioritize the upcoming section");
        if (controller.gpuBudgetBytes() != 64L * 1024L * 1024L) throw new AssertionError("unexpected GPU budget");
        System.out.println("AdaptiveLodStreamingSmokeTest: PASS");
    }
}
