# Stage 64 — Coarse-first parent LOD scheduling

## Change
Parent mesh jobs receive a bounded priority bias based on hierarchy level. Level 0 keeps its original priority; coarser parent levels receive progressively lower queue scores. This helps build broad, low-detail far-field coverage before spending worker time refining smaller nearby regions.

The bias is applied after predictive and frustum-aware priority, so camera direction and visibility still influence ordering. Existing queue aging remains active to prevent old work from being permanently starved.

## Limits
This changes CPU build ordering only. It does not create terrain outside captured snapshots, add more worker threads, or guarantee a specific FPS. GLES uploads remain on the render thread and subject to existing upload/memory budgets.

## Validation
Stage 64 smoke tests cover unchanged leaf priority, increasing coarse-level preference, and invalid inputs. CI compilation is not a substitute for Android runtime profiling.
