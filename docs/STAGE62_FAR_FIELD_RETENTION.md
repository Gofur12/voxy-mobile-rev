# Stage 62 — Bounded far-field snapshot retention

## Purpose
Preserve terrain data after vanilla chunks unload so the renderer can continue using previously visited terrain as far-field LOD source data.

## Changes
- Chunk unload now marks the chunk's snapshots as recently retained instead of deleting them.
- The snapshot store uses access-ordered LRU retention with an 8,192-section default cap.
- Eviction removes both the snapshot and its sparse LOD-tree leaf.
- Explicit removeChunk remains destructive for invalidation; disconnect still clears all world data.
- Tests cover unload retention, explicit removal, and LRU eviction consistency.

## Limits
This preserves terrain that the client has already received. It cannot reveal never-loaded or never-visited terrain. The cap is a memory bound, not a world-distance setting; a separate LOD range control and persistent disk cache remain future work.
