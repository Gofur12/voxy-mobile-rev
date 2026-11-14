package dev.brezze.voxymobile.diagnostics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight runtime counters to distinguish an inactive mod from an active renderer with no geometry. */
public final class RuntimeDiagnostics {
    private static final AtomicLong chunkLoads = new AtomicLong();
    private static final AtomicLong chunkUnloads = new AtomicLong();
    private static final AtomicLong queuedSections = new AtomicLong();
    private static final AtomicLong capturedSections = new AtomicLong();
    private static final AtomicLong renderCallbacks = new AtomicLong();
    private static final AtomicLong lastLogNanos = new AtomicLong();
    private static final AtomicLong leafMeshes = new AtomicLong();
    private static final AtomicLong parentMeshes = new AtomicLong();
    private static final AtomicLong leafDraws = new AtomicLong();
    private static final AtomicLong parentDraws = new AtomicLong();
    private static final AtomicLong zeroDrawFrames = new AtomicLong();
    private static final AtomicLong frameCandidates = new AtomicLong();
    private static final AtomicLong frustumRejected = new AtomicLong();
    private static final AtomicLong missingMeshes = new AtomicLong();
    private static final AtomicLong staleParents = new AtomicLong();
    private static final AtomicLong neighborPairs = new AtomicLong();
    private static final AtomicLong seamCandidates = new AtomicLong();
    private static final AtomicLong seamDraws = new AtomicLong();
    private static final AtomicLong seamFrustumRejected = new AtomicLong();
    private static final AtomicBoolean neighborBatchTruncated = new AtomicBoolean();
    private static final AtomicBoolean shaderReady = new AtomicBoolean();
    private static final AtomicBoolean rendererFailed = new AtomicBoolean();
    private static volatile String lastError = "";
    private static volatile String seamPipelineStatus = "";

    private RuntimeDiagnostics() {}

    public static void chunkLoaded(int nonEmptySections) {
        chunkLoads.incrementAndGet();
        queuedSections.addAndGet(Math.max(0, nonEmptySections));
    }
    public static void chunkUnloaded() { chunkUnloads.incrementAndGet(); }
    public static void sectionCaptured() {
        capturedSections.incrementAndGet();
        queuedSections.updateAndGet(value -> Math.max(0L, value - 1L));
    }
    public static void renderCallback(int leaves, int parents) {
        renderCallbacks.incrementAndGet();
        leafMeshes.set(Math.max(0, leaves));
        parentMeshes.set(Math.max(0, parents));
    }
    /** Records actual draw submissions, separately from meshes merely resident in GPU memory. */
    public static void drawStats(int leaves, int parents) {
        int safeLeaves = Math.max(0, leaves);
        int safeParents = Math.max(0, parents);
        leafDraws.set(safeLeaves);
        parentDraws.set(safeParents);
        if (safeLeaves + safeParents == 0) zeroDrawFrames.incrementAndGet();
    }
    public static void shaderReady(boolean ready) { shaderReady.set(ready); }
    /** Neighbor batch is derived from the same selected-node snapshot used by the draw loop. */
    public static void neighborBatch(int neighbors, int seams, boolean truncated) {
        neighborPairs.set(Math.max(0, neighbors));
        seamCandidates.set(Math.max(0, seams));
        neighborBatchTruncated.set(truncated);
    }
    /** Actual seam draw submissions and conservative frustum rejections for the latest frame. */
    public static void seamDrawStats(int draws, int rejected) {
        seamDraws.set(Math.max(0, draws));
        seamFrustumRejected.set(Math.max(0, rejected));
    }
    /** Compact live CPU-build, upload-queue, and GPU-residency snapshot. */
    public static void seamPipelineStatus(String status) { seamPipelineStatus = status == null ? "" : status; }

    /** Snapshot of why candidate terrain did or did not reach glDrawElements. */
    public static void renderAudit(int candidates, int rejected, int missing, int stale) {
        frameCandidates.set(Math.max(0, candidates));
        frustumRejected.set(Math.max(0, rejected));
        missingMeshes.set(Math.max(0, missing));
        staleParents.set(Math.max(0, stale));
    }

    public static void rendererFailed(Throwable error) {
        rendererFailed.set(true);
        lastError = error == null ? "unknown" : error.toString();
    }
    public static boolean isRendererFailed() { return rendererFailed.get(); }

    /** Call from the client tick; emits at most one compact status line every ten seconds. */
    public static void logIfDue(long nowNanos) {
        long previous = lastLogNanos.get();
        if (previous != 0L && nowNanos - previous < 10_000_000_000L) return;
        if (!lastLogNanos.compareAndSet(previous, nowNanos)) return;
        System.out.println("[VoxyMobile] runtime status: chunkLoads=" + chunkLoads.get()
                + ", chunkUnloads=" + chunkUnloads.get()
                + ", queuedSections=" + queuedSections.get()
                + ", capturedSections=" + capturedSections.get()
                + ", renderCallbacks=" + renderCallbacks.get()
                + ", leafGpuMeshes=" + leafMeshes.get()
                + ", parentGpuMeshes=" + parentMeshes.get()
                + ", shaderReady=" + shaderReady.get()
                + ", leafDraws=" + leafDraws.get()
                + ", parentDraws=" + parentDraws.get()
                + ", zeroDrawFrames=" + zeroDrawFrames.get()
                + ", frameCandidates=" + frameCandidates.get()
                + ", frustumRejected=" + frustumRejected.get()
                + ", missingMeshes=" + missingMeshes.get()
                + ", staleParents=" + staleParents.get()
                + ", neighborPairs=" + neighborPairs.get()
                + ", seamCandidates=" + seamCandidates.get()
                + ", seamDraws=" + seamDraws.get()
                + ", seamFrustumRejected=" + seamFrustumRejected.get()
                + ", neighborBatchTruncated=" + neighborBatchTruncated.get()
                + (seamPipelineStatus.isEmpty() ? "" : ", " + seamPipelineStatus)
                + ", rendererFailed=" + rendererFailed.get()
                + (lastError.isEmpty() ? "" : ", lastError=" + lastError));
    }

    public static void resetForTests() {
        chunkLoads.set(0); chunkUnloads.set(0); queuedSections.set(0); capturedSections.set(0);
        renderCallbacks.set(0); lastLogNanos.set(0); leafMeshes.set(0); parentMeshes.set(0);
        leafDraws.set(0); parentDraws.set(0); zeroDrawFrames.set(0);
        frameCandidates.set(0); frustumRejected.set(0); missingMeshes.set(0); staleParents.set(0);
        neighborPairs.set(0); seamCandidates.set(0); seamDraws.set(0); seamFrustumRejected.set(0); neighborBatchTruncated.set(false); shaderReady.set(false);
        rendererFailed.set(false); lastError = ""; seamPipelineStatus = "";
    }
}
