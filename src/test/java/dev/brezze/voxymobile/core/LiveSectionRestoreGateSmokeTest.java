package dev.brezze.voxymobile.core;

import dev.brezze.voxymobile.core.ingest.LiveSectionRestoreGate;
import dev.brezze.voxymobile.core.voxel.SectionPos;

public final class LiveSectionRestoreGateSmokeTest {
    public static void main(String[] args) {
        LiveSectionRestoreGate gate = new LiveSectionRestoreGate();
        SectionPos changed = new SectionPos(-4, 7, 12);
        SectionPos untouched = new SectionPos(-3, 7, 12);
        if (!gate.shouldRestore(changed)) throw new AssertionError("fresh section should allow cache restore");
        gate.markLive(changed);
        if (gate.shouldRestore(changed)) throw new AssertionError("stale disk section must not overwrite live section");
        if (!gate.shouldRestore(untouched)) throw new AssertionError("unseen section should still restore from cache");
        if (gate.trackedSections() != 1) throw new AssertionError("live section tracking mismatch");
        gate.clear();
        if (!gate.shouldRestore(changed) || gate.trackedSections() != 0)
            throw new AssertionError("gate did not reset after reconciliation");
        System.out.println("LiveSectionRestoreGateSmokeTest: PASS (5 assertions)");
    }
}
