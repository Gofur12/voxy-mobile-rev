package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.lod.LodNode;
import dev.brezze.voxymobile.core.lod.LodNodeTraversal;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LodSelectionNeighborBridgeSmokeTest {
    public static void main(String[] args) {
        LodNode west = ready(new SectionPos(0, 0, 0), 0);
        LodNode east = ready(new SectionPos(1, 0, 0), 1);
        LodNode stacked = ready(new SectionPos(1, 4, 0), 1);
        List<LodNodeTraversal.Selection> active = Arrays.asList(
                new LodNodeTraversal.Selection(west, false),
                new LodNodeTraversal.Selection(east, false),
                new LodNodeTraversal.Selection(stacked, false));

        LodSelectionNeighborBridge.Result result = LodSelectionNeighborBridge.build(active, 16, 16);
        check(result.selectedCount() == 3, "reports live selected node count");
        check(result.selections().size() == 3, "retains exact selection snapshot");
        check(result.batch().regionCount() == 3, "converts selected nodes to regions");
        check(result.batch().neighborCount() == 1, "detects only horizontally and vertically overlapping neighbor");
        check(result.batch().seamCandidateCount() == 1, "LOD mismatch reaches seam candidate batch");
        check(!result.truncated(), "unlimited small snapshot is complete");
        check(result.batch().candidates().get(0).firstRegionIndex == 0
                && result.batch().candidates().get(0).secondRegionIndex == 1, "indices map to selection order");

        LodSelectionNeighborBridge.Result capped = LodSelectionNeighborBridge.build(active, 2, 16);
        check(capped.selections().size() == 2 && capped.regionLimitReached(), "region cap is reported");
        check(capped.truncated(), "region cap marks result incomplete");
        LodSelectionNeighborBridge.Result empty = LodSelectionNeighborBridge.build(Collections.emptyList(), 0, 0);
        check(empty.selectedCount() == 0 && empty.batch().neighborCount() == 0, "empty selection is safe");
        expectFailure(() -> LodSelectionNeighborBridge.build(null, 1, 1));
        expectFailure(() -> LodSelectionNeighborBridge.build(active, -1, 1));
        expectFailure(() -> LodSelectionNeighborBridge.build(active, 1, -1));
        System.out.println("LodSelectionNeighborBridgeSmokeTest PASS (12 assertions)");
    }

    private static LodNode ready(SectionPos pos, int level) {
        LodNode node = new LodNode(pos, level);
        if (!node.markGeometryReady(node.contentRevision())) throw new AssertionError("node geometry setup failed");
        return node;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
