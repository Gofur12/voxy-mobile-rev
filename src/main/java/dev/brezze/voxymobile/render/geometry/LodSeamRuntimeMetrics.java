package dev.brezze.voxymobile.render.geometry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe counters and gauges for the live seam CPU-to-GPU pipeline. */
public final class LodSeamRuntimeMetrics {
    public record Snapshot(long candidates, long buildsScheduled, long buildsGenerated,
                           long buildsFailed, long staleResults, long uploadsAccepted,
                           long uploadsRejected, long uploadsCompleted, long meshesRemoved,
                           int activeCandidates, int pendingBuilds, int completedBuilds,
                           int pendingUploads, int gpuMeshes, long gpuBytes) {
        @Override public String toString() {
            return "seamCandidates=" + candidates + ", seamBuildsScheduled=" + buildsScheduled
                    + ", seamBuildsGenerated=" + buildsGenerated + ", seamBuildsFailed=" + buildsFailed
                    + ", seamStaleResults=" + staleResults + ", seamUploadsAccepted=" + uploadsAccepted
                    + ", seamUploadsRejected=" + uploadsRejected + ", seamUploadsCompleted=" + uploadsCompleted
                    + ", seamMeshesRemoved=" + meshesRemoved + ", seamActiveCandidates=" + activeCandidates
                    + ", seamPendingBuilds=" + pendingBuilds + ", seamCompletedBuilds=" + completedBuilds
                    + ", seamPendingUploads=" + pendingUploads + ", seamGpuMeshes=" + gpuMeshes
                    + ", seamGpuBytes=" + gpuBytes;
        }
    }

    private final AtomicLong candidates = new AtomicLong();
    private final AtomicLong buildsScheduled = new AtomicLong();
    private final AtomicLong buildsGenerated = new AtomicLong();
    private final AtomicLong buildsFailed = new AtomicLong();
    private final AtomicLong staleResults = new AtomicLong();
    private final AtomicLong uploadsAccepted = new AtomicLong();
    private final AtomicLong uploadsRejected = new AtomicLong();
    private final AtomicLong uploadsCompleted = new AtomicLong();
    private final AtomicLong meshesRemoved = new AtomicLong();
    private final AtomicInteger activeCandidates = new AtomicInteger();
    private final AtomicInteger pendingBuilds = new AtomicInteger();
    private final AtomicInteger completedBuilds = new AtomicInteger();
    private final AtomicInteger pendingUploads = new AtomicInteger();
    private final AtomicInteger gpuMeshes = new AtomicInteger();
    private final AtomicLong gpuBytes = new AtomicLong();

    public void activeCandidates(int value) { activeCandidates.set(Math.max(0, value)); candidates.addAndGet(Math.max(0, value)); }
    public void buildScheduled() { buildsScheduled.incrementAndGet(); }
    public void buildCompleted(boolean generated) { if (generated) buildsGenerated.incrementAndGet(); else buildsFailed.incrementAndGet(); }
    public void staleResult() { staleResults.incrementAndGet(); }
    public void uploadQueued(boolean accepted) { if (accepted) uploadsAccepted.incrementAndGet(); else uploadsRejected.incrementAndGet(); }
    public void uploadCompleted() { uploadsCompleted.incrementAndGet(); }
    public void meshRemoved() { meshesRemoved.incrementAndGet(); }
    public void gauges(int pending, int completed, int uploads, int meshes, long bytes) {
        pendingBuilds.set(Math.max(0, pending)); completedBuilds.set(Math.max(0, completed));
        pendingUploads.set(Math.max(0, uploads)); gpuMeshes.set(Math.max(0, meshes)); gpuBytes.set(Math.max(0L, bytes));
    }
    public Snapshot snapshot() {
        return new Snapshot(candidates.get(), buildsScheduled.get(), buildsGenerated.get(), buildsFailed.get(),
                staleResults.get(), uploadsAccepted.get(), uploadsRejected.get(), uploadsCompleted.get(),
                meshesRemoved.get(), activeCandidates.get(), pendingBuilds.get(), completedBuilds.get(),
                pendingUploads.get(), gpuMeshes.get(), gpuBytes.get());
    }
}
