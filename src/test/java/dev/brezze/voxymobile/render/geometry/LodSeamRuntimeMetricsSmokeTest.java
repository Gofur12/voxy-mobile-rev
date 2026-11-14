package dev.brezze.voxymobile.render.geometry;

public final class LodSeamRuntimeMetricsSmokeTest {
    private static int assertions;
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        LodSeamRuntimeMetrics metrics = new LodSeamRuntimeMetrics();
        metrics.activeCandidates(4);
        metrics.buildScheduled(); metrics.buildScheduled();
        metrics.buildCompleted(true); metrics.buildCompleted(false);
        metrics.staleResult();
        metrics.uploadQueued(true); metrics.uploadQueued(false); metrics.uploadCompleted();
        metrics.meshRemoved();
        metrics.gauges(1, 2, 3, 4, 4096);
        LodSeamRuntimeMetrics.Snapshot s = metrics.snapshot();
        check(s.candidates() == 4, "candidate total");
        check(s.activeCandidates() == 4, "active candidate gauge");
        check(s.buildsScheduled() == 2, "scheduled builds");
        check(s.buildsGenerated() == 1 && s.buildsFailed() == 1, "build outcomes");
        check(s.staleResults() == 1, "stale result count");
        check(s.uploadsAccepted() == 1 && s.uploadsRejected() == 1, "upload handoff outcomes");
        check(s.uploadsCompleted() == 1 && s.meshesRemoved() == 1, "GPU lifecycle events");
        check(s.pendingBuilds() == 1 && s.completedBuilds() == 2 && s.pendingUploads() == 3, "queue gauges");
        check(s.gpuMeshes() == 4 && s.gpuBytes() == 4096, "GPU gauges");
        check(s.toString().contains("seamGpuBytes=4096"), "log formatting");
        System.out.println("LodSeamRuntimeMetricsSmokeTest: PASS (11 assertions)");
    }
}
