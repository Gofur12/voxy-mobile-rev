package dev.brezze.voxymobile.render.streaming;

/** Dependency-free Stage 20 directional-priority checks. */
public final class PredictiveStreamingControllerSmokeTest {
    public static void main(String[] args) {
        PredictiveStreamingController controller = new PredictiveStreamingController();
        double forward = controller.priorityDistanceSquared(0, 0, -100, 0, 0, 0,
                0, 0, -1, 0, 0, 0, 10_000);
        double behind = controller.priorityDistanceSquared(0, 0, 100, 0, 0, 0,
                0, 0, -1, 0, 0, 0, 10_000);
        if (!(forward < behind)) throw new AssertionError("view-forward work must outrank work behind the camera");

        double stationaryOpposite = controller.priorityDistanceSquared(0, 0, -100, 0, 0, 0,
                0, 0, 1, 0, 0, 0, 10_000);
        double movingForward = controller.priorityDistanceSquared(0, 0, -100, 0, 0, 0,
                0, 0, 1, 0, 0, -12, 10_000);
        if (!(movingForward < stationaryOpposite)) throw new AssertionError("movement direction must influence priority");

        double invalidView = controller.priorityDistanceSquared(100, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 400);
        if (invalidView != 400) throw new AssertionError("invalid view must preserve distance priority");

        double invalidScore = controller.priorityDistanceSquared(1, 0, 0, 0, 0, 0,
                0, 0, -1, 0, 0, 0, Double.NaN);
        if (invalidScore != Double.MAX_VALUE) throw new AssertionError("invalid score must be safely deprioritized");

        double nearForward = controller.priorityDistanceSquared(0, 0, -90, 0, 0, 0,
                0, 0, -1, 0, 0, 0, 8_100);
        double farBehind = controller.priorityDistanceSquared(0, 0, 110, 0, 0, 0,
                0, 0, -1, 0, 0, 0, 12_100);
        if (!(nearForward < farBehind)) throw new AssertionError("directional bias must remain bounded and distance-aware");

        System.out.println("PredictiveStreamingControllerSmokeTest: PASS (5 checks)");
    }
}
