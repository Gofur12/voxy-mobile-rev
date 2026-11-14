# Stage 68 — Long-range hierarchical LOD

## Change
- Raised the default sparse LOD hierarchy from level 6 to level 8.
- A level-8 parent covers 256 sections per axis, or 4096 world blocks.
- Existing distance selector extrapolates entry thresholds for levels above 6, so level 8 becomes a coarse far-field tier.
- Added a smoke test for shared root coverage, negative-coordinate alignment, 4096-block parent selection, and independent distance clipping.

## Why
At long distances, larger parent tiles reduce the number of independent parent regions needed to represent retained terrain. This is a hierarchy/selection improvement, not a new terrain source: it can only render terrain already captured from live chunks or restored from the persistent cache.

## Validation
The Stage 68 CI smoke test verifies the CPU-side hierarchy and traversal contract. It does not replace Android/GLES visual testing on Adreno 640.
