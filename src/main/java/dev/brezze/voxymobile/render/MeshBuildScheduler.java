package dev.brezze.voxymobile.render;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Single-worker, bounded, distance-prioritized CPU meshing scheduler. Never calls OpenGL. */
public final class MeshBuildScheduler implements AutoCloseable {
    private static final int MAX_PENDING = 128;
    private static final int MAX_COMPLETED = 128;
    private final Object lock = new Object();
    private final ArrayList<Job> pending = new ArrayList<>();
    private final Map<SectionPos, Job> latestPending = new HashMap<>();
    private final Map<SectionPos, Long> revisions = new HashMap<>();
    private final Map<SectionPos, RequestState> requested = new HashMap<>();
    private final ArrayBlockingQueue<Completed> completed = new ArrayBlockingQueue<>(MAX_COMPLETED);
    private final Thread worker;
    private long sequence;
    private boolean closed;

    public MeshBuildScheduler() {
        worker = new Thread(this::workLoop, "VoxyMobile-MeshWorker");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    /** Returns false only when a farther queued job had to be retained over this request. */
    public boolean request(VoxelSection section, int lodLevel, double distanceSquared) {
        if (section == null || !Double.isFinite(distanceSquared) || distanceSquared < 0) return false;
        synchronized (lock) {
            if (closed) return false;
            SectionPos pos = section.position();
            RequestState existing = requested.get(pos);
            if (existing != null && existing.section == section && existing.lodLevel == lodLevel) {
                // Refresh queued priority as the camera turns or changes travel direction.
                Job queued = latestPending.get(pos);
                if (queued != null && queued.distanceSquared != distanceSquared) {
                    pending.remove(queued);
                    Job refreshed = new Job(section, lodLevel, distanceSquared, queued.revision, sequence++);
                    pending.add(refreshed);
                    latestPending.put(pos, refreshed);
                    lock.notifyAll();
                }
                return true;
            }
            long revision = revisions.getOrDefault(pos, 0L) + 1L;
            revisions.put(pos, revision);
            Job previous = latestPending.remove(pos);
            if (previous != null) pending.remove(previous);
            Job job = new Job(section, lodLevel, distanceSquared, revision, sequence++);
            requested.put(pos, new RequestState(section, lodLevel, revision));
            if (pending.size() >= MAX_PENDING) {
                Job farthest = pending.stream().max(ORDER).orElse(null);
                if (farthest == null || ORDER.compare(job, farthest) >= 0) {
                    requested.remove(pos);
                    lock.notifyAll();
                    return false;
                }
                pending.remove(farthest);
                latestPending.remove(farthest.section.position(), farthest);
                requested.remove(farthest.section.position());
            }
            pending.add(job);
            latestPending.put(pos, job);
            lock.notifyAll();
            return true;
        }
    }

    public void invalidate(SectionPos pos) {
        synchronized (lock) {
            revisions.put(pos, revisions.getOrDefault(pos, 0L) + 1L);
            requested.remove(pos);
            Job job = latestPending.remove(pos);
            if (job != null) pending.remove(job);
        }
        completed.removeIf(item -> item.data.source().position().equals(pos));
    }

    public MeshBuildData pollCompleted() {
        Completed item;
        while ((item = completed.poll()) != null) {
            synchronized (lock) {
                SectionPos pos = item.data.source().position();
                RequestState state = requested.get(pos);
                if (state == null || state.revision != item.revision || state.section != item.data.source()
                        || state.lodLevel != item.data.lodLevel()) continue;
                requested.remove(pos);
                return item.data;
            }
        }
        return null;
    }
    public boolean hasRequest(SectionPos pos, VoxelSection section, int lodLevel) {
        synchronized (lock) {
            RequestState state = requested.get(pos);
            return state != null && state.section == section && state.lodLevel == lodLevel;
        }
    }

    public int pendingCount() { synchronized (lock) { return pending.size(); } }
    public int completedCount() { return completed.size(); }

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
                latestPending.remove(job.section.position(), job);
            }
            MeshBuildData result;
            try { result = MeshBuildData.build(job.section, job.lodLevel); }
            catch (Throwable error) {
                synchronized (lock) {
                    if (revisions.getOrDefault(job.section.position(), 0L) == job.revision)
                        requested.remove(job.section.position());
                }
                System.err.println("[VoxyMobile] CPU mesh build failed for " + job.section.position() + ": " + error);
                continue;
            }
            synchronized (lock) {
                if (closed || revisions.getOrDefault(job.section.position(), 0L) != job.revision) continue;
            }
            Completed dropped = null;
            if (!completed.offer(new Completed(result, job.revision))) dropped = completed.poll();
            if (dropped != null) {
                synchronized (lock) {
                    SectionPos droppedPos = dropped.data.source().position();
                    RequestState state = requested.get(droppedPos);
                    if (state != null && state.revision == dropped.revision) requested.remove(droppedPos);
                }
                completed.offer(new Completed(result, job.revision));
            }
        }
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            pending.clear();
            latestPending.clear();
            requested.clear();
            lock.notifyAll();
        }
        worker.interrupt();
        completed.clear();
        try { worker.join(TimeUnit.SECONDS.toMillis(2)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static final Comparator<Job> ORDER = Comparator
            .comparingDouble((Job job) -> job.distanceSquared)
            .thenComparingLong(job -> job.sequence);

    private static final class Completed {
        final MeshBuildData data;
        final long revision;
        Completed(MeshBuildData data, long revision) { this.data = data; this.revision = revision; }
    }

    private static final class RequestState {
        final VoxelSection section;
        final int lodLevel;
        final long revision;
        RequestState(VoxelSection section, int lodLevel, long revision) {
            this.section = section; this.lodLevel = lodLevel; this.revision = revision;
        }
    }

    private static final class Job {
        final VoxelSection section;
        final int lodLevel;
        final double distanceSquared;
        final long revision, sequence;
        Job(VoxelSection section, int lodLevel, double distanceSquared, long revision, long sequence) {
            this.section = section; this.lodLevel = lodLevel; this.distanceSquared = distanceSquared;
            this.revision = revision; this.sequence = sequence;
        }
    }
}
