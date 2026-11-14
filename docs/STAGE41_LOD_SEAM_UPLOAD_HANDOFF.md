# Stage 41 — LOD seam upload handoff

## Added
- `LodSeamKey`: canonical, order-independent identity for a pair of distinct LOD regions.
- `LodSeamGpuMeshData`: immutable defensive-copy payload of XYZ+RGBA vertices and triangle indices, with revision and byte-size metadata.
- `LodSeamUploadQueue`: bounded, deduplicating, revision-aware CPU-to-render-thread handoff.
- `LodSeamUploadQueueSmokeTest`: validates key canonicalization, payload layout, defensive copies, stale revision rejection, replacement, capacity, removal and clearing.

## Threading contract
Mesh construction and queue offers are CPU-only. The queue never invokes OpenGL. Poll from the Minecraft render callback; only that caller may allocate/upload VBO/EBO. This stage deliberately stops before actual GLES allocation or draw submission.

## Validation boundary
The standalone Java 17 smoke test covers the new dependency-free classes. A full Gradle/Fabric build and Zalith/MobileGlues device test are not claimed by this stage.
