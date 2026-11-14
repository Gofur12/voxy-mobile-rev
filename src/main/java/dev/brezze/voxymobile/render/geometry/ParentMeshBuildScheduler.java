package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.lod.NodeDataSnapshot;
import dev.brezze.voxymobile.core.lod.ParentLodMeshBuilder;
import dev.brezze.voxymobile.core.lod.ParentVoxelAggregator;
import dev.brezze.voxymobile.core.lod.ParentLodMeshData;
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
        synchronized (lock) {
            if (closed) return false;
            RequestState state = requested.get(key);
            if (state != null && state.revision == snapshot.contentRevision()) {
                // Keep not-yet-started parent work aligned with the latest camera priority.
                Job queued = latestPending.get(key);
                if (queued != null && queued.distanceSquared != distanceSquared) {
                    pending.remove(queued);
                    Job refreshed = new Job(key, snapshot, distanceSquared, sequence++);
                    pending.add(refreshed);
                    latestPending.put(key, refreshed);
                    lock.notifyAll();
                }
                return true;
            }
            Job old = latestPending.remove(key);
            if (old != null) pending.remove(old);
            requested.put(key, new RequestState(snapshot.contentRevision()));
            Job job = new Job(key, snapshot, distanceSquared, sequence++);
            if (pending.size() >= MAX_PENDING) {
                Job farthest = pending.stream().max(ORDER).orElse(null);
                if (farthest == null || ORDER.compare(job, farthest) >= 0) {
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
                job = pending.stream().min(ORDER).orElse(null);
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

    private static final Comparator<Job> ORDER = Comparator.comparingDouble((Job j) -> j.distanceSquared)
            .thenComparingLong(j -> j.sequence);
    private record RequestState(long revision) {}
    private record Job(ParentGeometryKey key, NodeDataSnapshot snapshot, double distanceSquared, long sequence) {}
}
