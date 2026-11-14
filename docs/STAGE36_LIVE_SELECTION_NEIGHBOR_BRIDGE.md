# Stage 36 — Live LOD selection neighbor bridge

## Change
The renderer now snapshots `LodNodeTraversal.collectByDistance(...)` once and uses that same list for both the draw loop and a periodic neighbor scan. `LodSelectionNeighborBridge` converts selected nodes from section coordinates to world-space AABBs and feeds `LodNeighborBatch`.

Neighbor detection now supports optional Y bounds. The live bridge uses bounded regions, requiring vertical overlap before an X/Z edge can be considered adjacent. The older horizontal-only constructor remains available for existing tests/callers.

## Runtime budget
The bridge refreshes every ten render frames, considers at most 512 selected nodes, and retains at most 1,024 neighbor candidates. Any region or candidate cap is reported as truncation. Runtime status reports `neighborPairs`, `seamCandidates`, and `neighborBatchTruncated`.

## Validation and limits
The dependency-light smoke test verifies live selection conversion, world-space sizing, vertical separation rejection, seam candidate classification, caps, and invalid inputs. This does not yet make visible seam geometry: live side-surface extraction, seam patch creation from renderer meshes, GPU upload, and seam draw submission remain later work. Full Gradle/Fabric build and Zalith device validation are not claimed here.
