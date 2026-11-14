package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.lod.NodeDataSnapshot;
import dev.brezze.voxymobile.core.lod.ParentLodMeshBuilder;
import dev.brezze.voxymobile.core.lod.ParentVoxelAggregator;
import dev.brezze.voxymobile.core.lod.ParentLodMeshData;
import dev.brezze.voxymobile.render.streaming.QueuePriorityPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Bounded single-worker parent aggregation/meshing scheduler. It never touches OpenGL. */
public final class ParentMeshBuildScheduler implements AutoCloseable {
    private static final int MAX_PENDING = 48;
    private static final int MAX_COMPLETED = 48;
    private final Object lock = new Object();
    private final QueuePriorityPolicy priorityPolicy = new QueuePriorityPolicy();
    private final ArrayList<Job> pending = new ArrayList<>();
    private final Map<ParentGeometryKey, Job> latestPending = new HashMap<>();
    private final Map<ParentGeometryKey, RequestState> requested = new HashMap<>();
    private final ArrayBlockingQueue<ParentGpuMeshData> completed = new ArrayBlockingQueue<>(MAX_COMPLETED);
    private final Thread worker;
    private long sequence;
    private boolean closed;

    public ParentMeshBuildScheduler() {
        worker = new Thread(this::workLoop, "VoxyMobile-ParentMeshWorker");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 2);
        worker.start();
    }

    public boolean request(NodeDataSnapshot snapshot, double distanceSquared) {
        if (snapshot == null || !Double.isFinite(distanceSquared) || distanceSquared < 0) return false;
        ParentGeometryKey key = new ParentGeometryKey(snapshot.origin(), snapshot.level());
        double lodAwarePriority = lodAwarePriority(snapshot, distanceSquared);
        synchronized (lock) {
            if (closed) return false;
            RequestState state = requested.get(key);
            if (state != null && state.revision == snapshot.contentRevision()) {
                // Keep not-yet-started parent work aligned with the latest camera priority.
                Job queued = latestPending.get(key);
                if (queued != null && queued.distanceSquared != lodAwarePriority) {
                    pending.remove(queued);
                    Job refreshed = new Job(key, snapshot, lodAwarePriority, sequence++, queued.enqueuedNanos);
                    pending.add(refreshed);
                    latestPending.put(key, refreshed);
                    lock.notifyAll();
                }
                return true;
            }
            Job old = latestPending.remove(key);
            if (old != null) pending.remove(old);
            requested.put(key, new RequestState(snapshot.contentRevision()));
            Job job = new Job(key, snapshot, lodAwarePriority, sequence++, System.nanoTime());
            if (pending.size() >= MAX_PENDING) {
                Job farthest = worstPending(System.nanoTime());
                if (farthest == null || effective(job, System.nanoTime()) >= effective(farthest, System.nanoTime())) {
                    requested.remove(key);
                    return false;
                }
                pending.remove(farthest);
                latestPending.remove(farthest.key, farthest);
                requested.remove(farthest.key);
            }
            pending.add(job);
            latestPending.put(key, job);
            lock.notifyAll();
            return true;
        }
    }

    /**
     * Normalize camera distance by the node's world-space span. At the same distance,
     * coarse nodes receive earlier CPU build priority so a usable far-field silhouette
     * is produced before spending work on finer child meshes.
     */
    public static double lodAwarePriority(NodeDataSnapshot snapshot, double distanceSquared) {
        if (snapshot == null || !Double.isFinite(distanceSquared) || distanceSquared < 0.0)
            return Double.MAX_VALUE;
        double spanBlocks = Math.scalb(16.0, snapshot.level());
        double normalized = distanceSquared / (spanBlocks * spanBlocks);
        // Tiny deterministic level bias breaks equal normalized-distance ties in favor of coarser nodes.
        return normalized / (1.0 + snapshot.level() * 0.015);
    }

    public void invalidate(ParentGeometryKey key) {
        synchronized (lock) {
            requested.remove(key);
            Job job = latestPending.remove(key);
            if (job != null) pending.remove(job);
        }
        completed.removeIf(data -> data.key().equals(key));
    }

    public ParentGpuMeshData pollCompleted() {
        ParentGpuMeshData data;
        while ((data = completed.poll()) != null) {
            synchronized (lock) {
                RequestState state = requested.get(data.key());
                if (state == null || state.revision != data.contentRevision()) continue;
                requested.remove(data.key());
                return data;
            }
        }
        return null;
    }

    public boolean hasRequest(ParentGeometryKey key, long revision) {
        synchronized (lock) {
            RequestState state = requested.get(key);
            return state != null && state.revision == revision;
        }
    }

    public int pendingCount() { synchronized (lock) { return pending.size(); } }
    public int completedCount() { return completed.size(); }
    public int inFlightCount() { synchronized (lock) { return requested.size(); } }

    private void workLoop() {
        while (true) {
            Job job;
            synchronized (lock) {
                while (!closed && pending.isEmpty()) {
                    try { lock.wait(); } catch (InterruptedException ignored) { if (closed) return; }
                }
                if (closed) return;
                job = bestPending(System.nanoTime());
                if (job == null) continue;
                pending.remove(job);
                latestPending.remove(job.key, job);
            }
            ParentGpuMeshData result;
            try {
                var voxels = ParentVoxelAggregator.aggregate(job.snapshot.tree());
                ParentLodMeshData mesh = ParentLodMeshBuilder.build(voxels);
                result = ParentGpuMeshData.from(mesh, job.snapshot.contentRevision());
            } catch (Throwable error) {
                synchronized (lock) {
                    RequestState state = requested.get(job.key);
                    if (state != null && state.revision == job.snapshot.contentRevision()) requested.remove(job.key);
                }
                System.err.println("[VoxyMobile] Parent mesh build failed for " + job.key + ": " + error);
                continue;
            }
            synchronized (lock) {
                RequestState state = requested.get(job.key);
                if (closed || state == null || state.revision != result.contentRevision()) continue;
            }
            if (!completed.offer(result)) {
                ParentGpuMeshData dropped = completed.poll();
                if (dropped != null) synchronized (lock) {
                    RequestState state = requested.get(dropped.key());
                    if (state != null && state.revision == dropped.contentRevision()) requested.remove(dropped.key());
                }
                completed.offer(result);
            }
        }
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true; pending.clear(); latestPending.clear(); requested.clear(); lock.notifyAll();
        }
        worker.interrupt(); completed.clear();
        try { worker.join(TimeUnit.SECONDS.toMillis(2)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Job bestPending(long now) {
        Job best = null; double bestScore = Double.MAX_VALUE;
        for (Job candidate : pending) {
            double score = effective(candidate, now);
            if (best == null || score < bestScore || (score == bestScore && candidate.sequence < best.sequence)) {
                best = candidate; bestScore = score;
            }
        }
        return best;
    }
    private Job worstPending(long now) {
        Job worst = null; double worstScore = -1.0;
        for (Job candidate : pending) {
            double score = effective(candidate, now);
            if (worst == null || score > worstScore || (score == worstScore && candidate.sequence > worst.sequence)) {
                worst = candidate; worstScore = score;
            }
        }
        return worst;
    }
    private double effective(Job job, long now) {
        return priorityPolicy.effectivePriority(job.distanceSquared, job.enqueuedNanos, now);
    }
    private record RequestState(long revision) {}
    private record Job(ParentGeometryKey key, NodeDataSnapshot snapshot, double distanceSquared, long sequence, long enqueuedNanos) {}
}
