# Stage 17 — Multi-Level Parent Generation & Hierarchical Streaming

## Changes

- Parent generation is scheduled for every populated internal node (levels 1 through the tree maximum), not only level 1.
- The existing immutable subtree snapshot and revision checks remain the worker boundary.
- The bounded priority scheduler orders work by camera-distance score; nearby parent regions are submitted before farther regions.
- The renderer keeps the existing level-1 preferred traversal policy. Higher-level parent meshes are available as conservative fallback when finer descendants are not ready, so this stage does not claim a new distance-based LOD selection algorithm.
- Parent GPU allocations at all levels share the existing byte budget and render-thread-only GL lifecycle.

## Cost and limits

- A high-level parent snapshot includes its populated descendants. Building several ancestor levels can repeat aggregation work; this is intentionally bounded by the existing pending/completed queue and one worker.
- The renderer still uses the existing global desired level of 1. Level 2+ geometry is used as fallback rather than selected as a normal distance band.
- No runtime performance, visual quality, or Adreno 640 compatibility claim is made without a Zalith/MobileGlues test.

## Validation

`ParentMeshBuildSchedulerSmokeTest` now includes a two-level hierarchy case that submits level 1 and level 2 parent snapshots and verifies that both levels produce distinct revision-tagged geometry.
