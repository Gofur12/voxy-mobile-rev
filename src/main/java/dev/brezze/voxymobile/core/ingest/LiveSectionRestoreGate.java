package dev.brezze.voxymobile.core.ingest;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import java.util.HashSet;
import java.util.Set;

/**
 * Prevents delayed disk-cache results from overwriting sections already reconciled
 * against live Minecraft chunk data during the current cache-restore window.
 */
public final class LiveSectionRestoreGate {
    private final Set<SectionPos> liveSections = new HashSet<>();

    public synchronized void markLive(SectionPos position) {
        if (position != null) liveSections.add(position);
    }

    public synchronized boolean shouldRestore(SectionPos position) {
        return position != null && !liveSections.contains(position);
    }

    /** Called only after disk loading and all queued live section refreshes settle. */
    public synchronized void clear() {
        liveSections.clear();
    }

    public synchronized int trackedSections() { return liveSections.size(); }
}
