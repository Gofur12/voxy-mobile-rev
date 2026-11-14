# Stage 75 — Bounded draw traversal

- The renderer's distance-adaptive draw traversal previously built a full node snapshot and iterated every root before pruning by radius.
- Adds `rootsWithinDistance` and switches `LodNodeTraversal.collectByDistance` to bounded root and node queries.
- The same LOD radius now bounds both parent scheduling and the actual draw-selection traversal, reducing per-frame CPU work for large retained worlds.
- Adds smoke coverage that emitted draw selections remain inside the configured radius.
- No terrain is synthesized for never-visited chunks; Android visual verification remains required.
