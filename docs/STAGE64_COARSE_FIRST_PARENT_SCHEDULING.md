# Stage 64 — Coarse-first far-field parent scheduling

## Change
Parent build priority is normalized by each node's world-space span. At equal camera distance, higher/coarser hierarchy levels receive a slightly better score, allowing the renderer to prepare broad far-field coverage before spending CPU time on finer parent meshes.

The existing bounded single-worker queue, priority aging, stale-revision checks, and render-thread-only GPU uploads remain in place.

## Why
A far LOD system should establish coarse silhouettes first, then refine detail as CPU queue capacity becomes available. This stage changes scheduling order, not terrain acquisition.

## Limits
- No new terrain is generated or downloaded.
- The scheduler still builds one parent mesh per worker job.
- Actual visual improvement and frame-time behavior require Android testing.
