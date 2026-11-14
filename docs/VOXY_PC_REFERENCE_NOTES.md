# Voxy PC reference inspection

Reference file supplied in this conversation: `voxy-0.2.18-beta-26.1.2.jar`.

## Compatibility boundary

The embedded Fabric metadata identifies Minecraft `26.1.2` and Java `>=25`. VoxyMobile Reborn targets Minecraft `1.20.1`, Fabric and Java 17. The reference JAR is an architectural reference only; its classes, mixins, rendering hooks, dependencies and bytecode are not binary/source-compatible with this project.

Inspection performed from the supplied JAR:
- Read `fabric.mod.json` and enumerated relevant rendering, geometry, world and storage classes.
- Used `javap -p -s` on the actual packaged classes to inspect their declared fields, constructors and method signatures.
- This is structural/API inspection, not full source decompilation or proof that the algorithms have been reproduced.

## Relevant Voxy architecture observed

| Voxy class | Directly observed structure | Mobile adaptation |
|---|---|---|
| `me.cortex.voxy.client.core.rendering.building.RenderGenerationService` | `PriorityBlockingQueue<BuildTask>`, per-section task map protected by `StampedLock`, worker `Service`, result consumer, queue/holding counters | Bounded `MeshBuildScheduler`, deduplication/revision checks and movement-aware priority from Stage 07/11 |
| `me.cortex.voxy.client.core.rendering.building.RenderDataFactory` | World/model factory references, section data, neighboring-face data, opaque/non-opaque/fluid masks, occupancy and mesher instances | Existing Stage 04 greedy surface mesher; full Minecraft block-model baking is not claimed |
| `me.cortex.voxy.client.core.rendering.section.geometry.BasicAsyncGeometryManager` | Async geometry manager with section metadata, capacity/allocation and upload/removal lifecycle (class signature inspected) | Stage 05 render-thread GPU upload and Stage 11 estimated resident geometry budget |
| `me.cortex.voxy.client.core.rendering.RenderDistanceTracker` | Center coordinates, render-distance bounds, ring tracker, incremental add/remove callbacks | Stage 11 camera movement prediction and adaptive LOD policy |
| `me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager` | Async node manager with dedicated service/queues and GPU/compute-related resources (class signature inspected) | CPU-side sparse hierarchy and bounded worker queues; no desktop compute path copied |
| `me.cortex.voxy.client.core.rendering.hierachical.NodeManager` | Top-level node sets, active-section map, child/single request allocation, geometry manager and node updates | Stage 12 sparse `LodNodeTree`, incremental child insertion/removal, content revisions |
| `me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser` | Traversal pipeline with node/request/render queue buffers, scene uniforms, Hi-Z sampler and traversal shader | Stage 08–10 CPU conservative visibility; compute/SSBO/Hi-Z path deliberately not copied into GLES 3.2 target |
| `me.cortex.voxy.client.core.rendering.GeometryCache` | Lock-protected LRU-like linked cache of built sections with max/current size tracking | Stage 11 GPU byte budget and distance-based eviction; full cache parity is not claimed |

## Stage 12 scope

Stage 12 adapts the sparse parent/child node structure and readiness/fallback contract. It does not claim to reproduce Voxy's packed node IDs, asynchronous node requests, world storage, geometry bakery, Hi-Z traversal or GPU node buffers. Parent aggregate voxel generation and parent GPU mesh submission remain future work.

## Stage 13 reference adaptation

Stage 13 adds a CPU parent-voxel aggregation and greedy parent-quad builder. This is a limited mobile-side counterpart to Voxy's separation between render-data generation (`RenderDataFactory`), queued generation (`RenderGenerationService`) and hierarchical node geometry updates (`NodeManager` / `AsyncNodeManager`). It is not a port of Voxy's world/model-aware opaque, non-opaque and fluid meshing: the current VoxyMobile input is still integer material IDs with air represented by zero. Parent quad data remains CPU-only until a dedicated node-geometry cache and GLES submission path is implemented.


## Stage 14 reference adaptation

Stage 14 adds a `ParentGeometryCache` with byte accounting and access-ordered LRU eviction, corresponding to the observed capacity-tracked `GeometryCache` concept. `ParentGpuMeshData` converts parent greedy quads into the existing GLES vertex/index layout and expands coordinates by the parent voxel scale. `ParentGeometryUploadQueue` provides a bounded, revision-deduplicating CPU-to-render-thread handoff. These are mobile-side adaptations only: no Voxy cache implementation or GPU manager code is copied, and the queue is not yet wired to actual GL object creation or draw submission.


## Stage 15 adaptation

The hierarchical renderer now consumes the project's own sparse `LodNodeTree` and `LodNodeTraversal` rather than adopting Voxy's compute/SSBO/Hi-Z traversal. Level-1 parent meshes use the GLES 3.x VAO/VBO/EBO path already used for leaf geometry. Parent revisions are checked before upload and draw, and the traversal selection is exclusive across parent/child coverage. This is a conservative GLES-compatible adaptation, not a port of Voxy's GPU traversal implementation.


## Stage 15 reference adaptation

Stage 15 connects the project's own sparse `LodNodeTree` and `LodNodeTraversal` to the GLES 3.x renderer. Level-1 parent meshes use VAO/VBO/EBO uploads, exact content revisions, and exclusive parent-versus-descendant selection. This deliberately avoids Voxy's compute/SSBO/Hi-Z traversal path and remains a conservative CPU/GLES implementation.
