# Stage 16 — Async Parent Generation & GPU Memory Manager

## Thread ownership

- Client/render thread: obtains the LOD tree's immutable structural snapshot, schedules work, checks revisions, uploads buffers, submits draws, and deletes GL resources.
- `VoxyMobile-ParentMeshWorker`: performs parent voxel aggregation, greedy meshing, and conversion to immutable GLES-ready CPU arrays.
- The worker never imports or calls LWJGL/OpenGL APIs.

## Snapshot and revision contract

`LodNodeTree.snapshotNodeData(origin, level)` captures the subtree shape and its content revision while holding the tree monitor. The snapshot only retains immutable `VoxelSection` references and immutable node structure. The worker builds exclusively from that snapshot. Before upload, the render thread resolves the node again and rejects the result if the node disappeared or its revision changed.

## Queue bounds

- Pending parent jobs: 48 maximum.
- Completed parent payloads: 48 maximum.
- One low-priority daemon worker.
- Requests are deduplicated by `(origin, level, contentRevision)`; a newer revision replaces a queued older request.
- Queue overflow drops work rather than blocking the render callback. The renderer can request it again on a later frame.

## GPU memory policy

Leaf and parent VAO/VBO/EBO allocations contribute to one byte counter. When the streaming budget is exceeded, the renderer evicts the farthest unprotected allocation, whether it is a leaf or parent. Eviction deletes GL resources on the render thread and invalidates the matching LOD geometry-ready flag so traversal can choose available fallback geometry.

The policy is distance-based, not a true recency-based LRU. If a single protected allocation exceeds the budget, it remains resident rather than being deleted while in use.

## Validation

`ParentMeshBuildSchedulerSmokeTest` checks atomic revision snapshots, asynchronous mesh generation, invalidation of stale work, and scheduler shutdown. Existing core, LOD, aggregation, and geometry smoke tests are retained. A full Fabric/Gradle build and actual Zalith + MobileGlues GPU run remain external validation tasks.
