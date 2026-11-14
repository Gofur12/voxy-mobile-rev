package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.lod.LodNode;
import dev.brezze.voxymobile.core.lod.LodNodeTraversal;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts the renderer's actual hierarchical selections into bounded, 3D-aware neighbor batches. */
public final class LodSelectionNeighborBridge {
    private LodSelectionNeighborBridge() {}

    public static final class Result {
        private final List<LodNodeTraversal.Selection> selections;
        private final LodNeighborBatch.Result batch;
        private final int selectedCount;
        private final boolean regionLimitReached;

        private Result(List<LodNodeTraversal.Selection> selections, LodNeighborBatch.Result batch,
                       int selectedCount, boolean regionLimitReached) {
            this.selections = Collections.unmodifiableList(new ArrayList<>(selections));
            this.batch = batch;
            this.selectedCount = selectedCount;
            this.regionLimitReached = regionLimitReached;
        }
        public List<LodNodeTraversal.Selection> selections() { return selections; }
        public LodNeighborBatch.Result batch() { return batch; }
        public int selectedCount() { return selectedCount; }
        public boolean regionLimitReached() { return regionLimitReached; }
        public boolean truncated() { return regionLimitReached || batch.truncated(); }
    }

    /**
     * Uses the exact selection snapshot that the renderer will draw. Coordinates are converted
     * from section units to world blocks; vertical overlap prevents false X/Z neighbors on
     * stacked terrain levels. Limits bound both region conversion and retained candidates.
     */
    public static Result build(List<LodNodeTraversal.Selection> activeSelections,
                               int maxRegions, int maxCandidates) {
        if (activeSelections == null) throw new IllegalArgumentException("active selections must not be null");
        if (maxRegions < 0 || maxCandidates < 0) throw new IllegalArgumentException("limits must be >= 0");
        List<LodNodeTraversal.Selection> used = new ArrayList<>();
        List<LodNeighborDetector.Region> regions = new ArrayList<>();
        int selectedCount = activeSelections.size();
        int limit = Math.min(selectedCount, maxRegions);
        for (int i = 0; i < limit; i++) {
            LodNodeTraversal.Selection selection = activeSelections.get(i);
            if (selection == null || selection.node() == null)
                throw new IllegalArgumentException("active selections must not contain null");
            LodNode node = selection.node();
            SectionPos origin = node.origin();
            double span = node.spanSections() * 16.0;
            double minX = origin.x() * 16.0;
            double minY = origin.y() * 16.0;
            double minZ = origin.z() * 16.0;
            used.add(selection);
            regions.add(new LodNeighborDetector.Region(minX, minY, minZ,
                    minX + span, minY + span, minZ + span, node.level()));
        }
        LodNeighborBatch.Result batch = LodNeighborBatch.build(regions, maxCandidates);
        return new Result(used, batch, selectedCount, selectedCount > maxRegions);
    }
}
