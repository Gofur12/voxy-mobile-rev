# Stage 74 — Bounded hierarchy query

- Adds LodNodeTree.nodesWithinDistance, pruning root and child branches using world-space AABB distance before adding nodes to the result snapshot.
- GLES parent scheduling now uses this bounded snapshot instead of copying every hierarchy node each frame.
- Selector hysteresis is retained only for nodes inside the active LOD radius; existing parent-mesh cleanup still removes GPU allocations outside that radius.
- Smoke coverage verifies that distant branches are omitted and a smaller radius returns fewer nodes.
- This reduces CPU traversal and temporary-list allocation as the persistent terrain tree grows. It does not generate data for unexplored chunks and is not Android visual verification.
