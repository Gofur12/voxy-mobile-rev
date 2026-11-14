# VoxyMobile Reborn — Stage 20: Predictive Streaming

Target: Minecraft Java 1.20.1 / Fabric / Java 17; Android Zalith Launcher + MobileGlues GLES 3.x; POCO X3 Pro / Snapdragon 860 / Adreno 640 is the primary development target.

## Stage 20 implementation

- Adds view-direction and movement-direction-aware predictive scheduling for leaf and parent LOD mesh jobs.
- Uses the existing predicted camera position as the base priority, then applies a bounded directional multiplier so distance remains the dominant signal.
- Refreshes queued job priorities as the camera turns or changes travel direction; already-running worker tasks are not cancelled.
- Duplicate outstanding jobs do not consume the per-frame budget for *new* requests.
- Preserves bounded queues, worker-thread CPU meshing, revision checks, render-thread GLES uploads, and hierarchical parent fallback.

See [`docs/STAGE20_PREDICTIVE_STREAMING.md`](docs/STAGE20_PREDICTIVE_STREAMING.md) for policy details and limits.

## Stage 19 implementation

- Adds a CPU-only frame-time feedback controller with exponential moving average and asymmetric hysteresis.
- Sustained frame-time pressure moves through four quality tiers; isolated spikes do not immediately change quality.
- Lower tiers scale leaf LOD distance boundaries inward, selecting coarser leaf meshes sooner.
- Per-frame leaf mesh requests, parent mesh requests, and completed uploads are bounded by the active quality tier.
- Frame-time samples are taken from the interval between renderer callbacks; this includes pacing/vsync and is not a GPU timer query.

## Validation

Dependency-free Java smoke tests can be run with Java 17. With Gradle 8.6 and JDK 17, run:

```bash
gradle build stage11SmokeTest stage12SmokeTest stage13SmokeTest stage14SmokeTest stage15SmokeTest stage16SmokeTest stage17SmokeTest stage18SmokeTest stage19SmokeTest stage20SmokeTest --no-daemon
```

The complete Gradle/Fabric build and Zalith + MobileGlues runtime have not been tested in this environment. Directional priority is a CPU scheduling heuristic, not a guarantee of zero pop-in; on-device profiling is still required.

## Voxy PC reference boundary

The supplied Voxy JAR was inspected structurally with `jar` and `javap`. Its hierarchical node traversal, asynchronous generation, and distance tracking informed design choices only. This project does not copy Voxy code or its compute/SSBO/Hi-Z renderer; it uses the GLES 3.x VAO/VBO/EBO path and targets Minecraft 1.20.1 / Java 17.


## Stage 21 — Visibility-Aware Streaming

Stage 21 combines Stage 20 predictive priorities with current-frustum visibility weighting for leaf and parent mesh generation. Off-screen and conservatively enclosed leaf sections are deprioritized rather than discarded, preserving recovery after camera turns and visibility changes. The implementation remains CPU-only and GLES 3.x compatible; it does not use Hi-Z, compute shaders, or SSBOs. See `docs/STAGE21_VISIBILITY_STREAMING.md`.

Build/test task: `gradle build stage11SmokeTest stage12SmokeTest stage13SmokeTest stage14SmokeTest stage15SmokeTest stage16SmokeTest stage17SmokeTest stage18SmokeTest stage19SmokeTest stage20SmokeTest stage21SmokeTest --no-daemon`.

## Stage 22 — Streaming Queue Intelligence

Stage 22 adds capped priority aging to leaf and parent mesh build queues. Older pending jobs gradually gain scheduling priority, while queue capacity, deduplication, revision validation, predictive priority and visibility weighting remain in place. Also includes the repository-mode and Fabric event import corrections identified by the recent GitHub Actions failures. See `docs/STAGE22_QUEUE_INTELLIGENCE.md`.

Build/test task: `gradle build stage11SmokeTest stage12SmokeTest stage13SmokeTest stage14SmokeTest stage15SmokeTest stage16SmokeTest stage17SmokeTest stage18SmokeTest stage19SmokeTest stage20SmokeTest stage21SmokeTest stage22SmokeTest --no-daemon`.

## Stage 23 — GPU Memory & Mesh Lifecycle Optimization

Stage 23 adds a shared GPU mesh budget policy with hysteresis: eviction starts above 90% of the 64 MiB allocation estimate and continues until usage falls to 78%. Existing farthest-first leaf/parent mesh eviction, revision checks and render-thread-only GL deletion are retained. Lightweight GPU allocation and pressure diagnostics are exposed by the renderer. The budget tracks submitted vertex/index buffer bytes, not driver-reported VRAM. Stage 20.2's Yarn 1.20.1 chunk-validation and raw block-state ID fixes are carried forward. See `docs/STAGE23_GPU_MEMORY_LIFECYCLE.md`.

## Stage 24 — GPU Upload Pipeline Optimization

Stage 24 adds a shared per-frame byte budget for leaf and parent mesh uploads. The default allowance is 2 MiB/frame and is reduced to 1.5 MiB, 1 MiB, or 512 KiB as the Stage 23 GPU-memory pressure level rises. Upload payload sizes are reserved before GLES buffer submission. If a single mesh exceeds the current frame allowance, it is allowed on an otherwise empty frame to avoid permanent starvation; later uploads wait for the next frame. Leaf payloads are deferred locally and parent payloads are returned to their bounded upload queue when the budget is exhausted. All GL calls remain on the render thread. This is a submission-byte budget, not true GPU DMA batching or driver VRAM telemetry. See `docs/STAGE24_GPU_UPLOAD_PIPELINE.md`.

Build/test task: `gradle build stage11SmokeTest stage12SmokeTest stage13SmokeTest stage14SmokeTest stage15SmokeTest stage16SmokeTest stage17SmokeTest stage18SmokeTest stage19SmokeTest stage20SmokeTest stage21SmokeTest stage22SmokeTest stage23SmokeTest stage24SmokeTest --no-daemon`.


## Stage 25 — Runtime Activation Diagnostics

Stage 25 adds compact client-log diagnostics to distinguish a mod initializer that never ran, chunk ingestion that is not occurring, an empty snapshot pipeline, render callbacks that are not firing, and a GLES renderer that disabled itself after an exception. The client log prints a startup marker and a status line at most every ten seconds with chunk loads/unloads, pending/captured sections, render callback count, Leaf/Parent GPU mesh counts, and renderer failure state. This is diagnostic instrumentation; it does not guarantee that the renderer produces visible terrain on every driver. Check `latest.log` for `[VoxyMobile]`.

Build/test task: `gradle build stage11SmokeTest stage12SmokeTest stage13SmokeTest stage14SmokeTest stage15SmokeTest stage16SmokeTest stage17SmokeTest stage18SmokeTest stage19SmokeTest stage20SmokeTest stage21SmokeTest stage22SmokeTest stage23SmokeTest stage24SmokeTest stage25SmokeTest --no-daemon`.


## Stage 27 — Camera-space frustum consistency

Stage 27 fixes a concrete coordinate-space mismatch in hierarchical draw culling. Mesh vertices are stored in world coordinates and the vertex shader converts them to camera-relative coordinates; leaf visibility already used camera-relative bounds, but parent-node draw rejection used world-space bounds. Parent draw bounds now use the same camera-relative conversion as leaf visibility and the streaming frustum. The new `CameraRelativeAabb` helper validates section and parent-node bounds, including negative coordinates and non-origin camera positions.

Run `gradle stage27SmokeTest` for the focused coordinate-space test. This stage does not claim that the full renderer is visually verified; that still requires the Stage 27 artifact on the target Zalith/MobileGlues setup.


## Stage 28 — Terrain draw-path audit

Stage 28 adds a compact per-frame audit of the hierarchical terrain draw decision path. It counts candidate nodes, frustum/visibility rejections, missing GPU meshes, stale parent revisions, and submitted leaf/parent draws. The counters are included in the periodic runtime status line so a Zalith test log can distinguish “no terrain candidates”, “culled”, “mesh not uploaded”, “stale parent”, and “draw submitted”. These counters diagnose submission; they do not prove pixels reached the screen.

Run `gradle stage28SmokeTest` for the pure-Java audit test. Full Fabric compilation and on-device visual verification remain required.


## Stage 29 — Conservative LOD seam policy

Stage 29 adds `LodSeamPolicy`, a pure-Java neighbor constraint that limits adjacent regions to a maximum one-level LOD difference. It flags larger gaps and reports how many intermediate levels would be needed. This is a conservative selection policy, not a geometric stitch or skirt generator; it does not yet eliminate every T-junction or guarantee a watertight terrain surface. The policy is isolated and tested before it is wired into the live traversal/selection path.

Run `gradle stage29SmokeTest` for the policy tests. Full Fabric build and visual validation on Zalith are still required.


## Stage 29 — Conservative LOD seam policy

Adds `LodSeamPolicy`, a tested neighbor constraint that limits adjacent regions to a maximum one-level LOD difference, flags larger gaps, and reports required intermediate levels. This is a selection policy, not a geometric stitch/skirt generator; it does not yet eliminate every T-junction or guarantee a watertight surface. It is isolated and tested, but not yet wired into live traversal/selection. Run `gradle stage29SmokeTest`. Full Fabric build and Zalith visual validation remain required.


## Stage 30 — CPU LOD seam stitching primitive

Stage 30 adds `LodSeamStitcher`, a pure-Java zipper triangulator that creates a triangle strip patch between two ordered boundary polylines with different vertex counts. It validates XYZ input, finite coordinates, and matching endpoints; the output indices address the concatenation of the two input edges. The primitive is deliberately isolated: it is not yet wired into `GlesLodRenderer`, mesh vertex packing, or GPU upload, so it does not yet guarantee visible seam closure in-game.

Run `gradle stage30SmokeTest` for the focused geometry test. Full Gradle/Fabric compilation and Zalith visual verification remain required.


## Stage 31 — GLES-ready seam patch payload

Stage 31 adds `LodSeamPatchData`, which consumes the Stage 30 zipper topology and packs both ordered boundary polylines into the renderer's existing seven-float XYZ+RGBA vertex layout plus 32-bit indices. It exposes defensive array copies, vertex/index/triangle counts, and exact client-side upload byte size, with color-range and seam-endpoint validation. This is now upload-ready CPU data, but it is **not yet submitted to GLES**: live leaf/parent neighbor-edge extraction and draw integration remain a separate step.

Run `gradle stage31SmokeTest` for payload layout, index bounds, byte accounting, defensive-copy, and invalid-input checks. Full Fabric build and Zalith/MobileGlues visual validation remain required.


## Stage 32 — Mesh Boundary Extraction

Adds `MeshBoundaryExtractor`, a CPU-only utility that extracts unique, ordered XYZ boundary points from the existing XYZ+RGBA packed mesh layout. The caller specifies a boundary plane, tangent axis, fixed axis/value, and tolerance. This stage does not yet create interpolated edge points, invoke zipper stitching automatically, upload seam buffers, or issue GLES draw calls. Run `gradle stage32SmokeTest` to execute its focused smoke test.


## Stage 33 — Automatic seam patch generation

Adds `LodSeamPatchGenerator`, which combines Stage 32 boundary extraction with Stage 30 zipper triangulation and Stage 31 payload packing. Callers provide two packed XYZ+RGBA mesh arrays and explicit axis-aligned `EdgeQuery` descriptions; the generator extracts both ordered edges, rejects missing boundaries or incompatible endpoints, and returns an immutable `LodSeamPatchData` payload. This remains CPU-side: it does not discover adjacent terrain sections, infer edge queries from topology, upload VBO/EBO buffers, or issue GLES draw calls. Run `gradle stage33SmokeTest` for the focused pipeline test.


## Stage 34 — LOD neighbor detection

Adds `LodNeighborDetector`, a CPU-side utility that detects shared axis-aligned horizontal edges between two LOD regions. It reports edge orientation, shared plane, positive overlap interval, and absolute LOD delta; gaps and corner-only contacts are rejected. Equal-LOD neighbors are identified but do not require a seam. This stage does not yet connect region metadata to live renderer nodes or issue GPU uploads/draw calls. Run `gradle stage34SmokeTest` for the focused test.


## Stage 35 — Active LOD neighbor batch bridge

Adds `LodNeighborBatch`, which accepts the frame's selected horizontal LOD regions and produces a deterministic list of unique adjacent-region pairs. Each candidate carries the Stage 34 shared-edge descriptor and identifies whether the pair has an LOD mismatch that needs seam work. The result reports region, neighbor, and seam-candidate counts. A configurable candidate cap bounds retained output; a truncated result is explicitly marked so callers cannot treat it as complete.

This is a CPU-side integration bridge, not yet wired to the renderer's live selected-node collection. It does not extract 3D terrain side surfaces, generate seam vertex payloads, upload VBO/EBO buffers, or issue draw calls. In particular, Stage 32's current line-edge extractor is not sufficient by itself to represent a full vertical terrain boundary. Run `gradle stage35SmokeTest` for the focused batch test.


## Stage 36 — Live LOD selection neighbor bridge

- Converts the exact `LodNodeTraversal.collectByDistance` selection used by the draw loop into world-space neighbor regions.
- Adds optional vertical bounds to neighbor detection so stacked Y-level regions are not mistaken for touching terrain.
- Rebuilds the bounded neighbor batch every ten render frames (512 regions / 1,024 retained candidates) and reports pair/seam counts plus truncation in runtime diagnostics.
- This is CPU-side live selection integration only: it does not yet extract terrain side surfaces, generate seam patch geometry from live mesh buffers, upload seam VBO/EBO data, or issue seam draw calls.


## Stage 37 — Live terrain mesh plane-boundary extraction

Adds `MeshPlaneBoundaryExtractor`, which reads the existing indexed XYZ+RGBA terrain mesh payload and intersects each triangle with an axis-aligned plane. It interpolates position and color at triangle-edge crossings, filters results to a caller-supplied overlap rectangle, skips fully coplanar triangles, and enforces a maximum segment count. The output is a bounded CPU-side list of independent line segments (two packed vertices per segment), not an ordered watertight contour. It is not yet connected to live GPU mesh handles, seam stitching, VBO/EBO upload, or GLES draw submission. Run `gradle stage37SmokeTest` for the focused geometry test. Full Gradle/Fabric build and Zalith/MobileGlues visual verification remain required.

## Stage 38 — Boundary contour stitching

Stage 38 adds a bounded CPU stitcher that joins unordered terrain boundary segments into ordered open contours or closed loops. It removes duplicate segments and preserves packed XYZ+RGBA point data. This is preparation for seam triangulation only; it is not yet a GPU-rendered seam.
See `docs/STAGE38_BOUNDARY_CONTOUR_STITCHING.md`.


## Stage 39 — Contour Surface Triangulation

Adds a CPU-only ear-clipping triangulator for simple, planar, closed XYZ+RGBA contours. It validates closure, finite coordinates, planarity, projected area, and triangulation progress; returns defensive triangle-index and normal copies. Open contours and non-simple/degenerate contours are rejected rather than guessed. This stage does not upload or draw seam geometry.


## Stage 40 — LOD Seam Surface Mesh

`LodSeamSurfaceMesh` combines the Stage 38 packed contour output with the Stage 39 planar contour triangulator. It removes a duplicated closing vertex, preserves per-vertex RGBA, validates generated indices, and exposes defensive copies of an indexed XYZ+RGBA CPU mesh plus its normal and byte-size estimate. This is a CPU-side mesh payload only: it is not yet connected to the GLES VBO/EBO upload or draw path, and it does not prove a visible terrain seam in-game.


## Stage 41 — LOD seam upload handoff

Stage 41 adds a canonical seam key, immutable XYZ+RGBA/index payload, and a bounded revision-aware queue. Worker-side code may construct and offer payloads; the Minecraft render thread polls them and is responsible for actual GLES VBO/EBO allocation and upload. This stage does **not** yet call OpenGL for seam meshes or draw them.

## Stage 42 — GLES seam buffer upload

Stage 42 adds render-thread-owned GLES VAO/VBO/EBO allocation for seam payloads. `GlesLodRenderer.enqueueSeamMesh` accepts CPU payloads from worker-side producers; the next world render callback polls a bounded queue and uploads within a 512 KiB per-frame seam budget (a single first payload may exceed the budget to avoid permanent starvation). Uploaded seam meshes are drawn with the existing terrain shader and are released with renderer teardown or `removeSeamMesh`. Seam generation is not yet automatically wired to the live neighbor batch; producers must enqueue a completed `LodSeamGpuMeshData`.


## Stage 43 — Live LOD seam generation

- Retains the CPU vertex/index arrays alongside uploaded leaf and parent meshes so the seam producer can inspect the exact geometry selected for rendering.
- On the existing ten-frame neighbor refresh, resolves each unequal-LOD selected pair to its current CPU mesh payloads.
- Intersects both indexed meshes with the shared X/Z boundary plane inside the shared vertical/horizontal overlap, stitches the resulting segments into open contours, and pairs contours only when both endpoints match.
- Builds a zipper seam payload, queues it through `enqueueSeamMesh`, and lets the existing render-thread GLES upload/draw path handle it. Missing or mismatched boundaries are rejected rather than guessed; stale seam buffers are removed when their pair disappears or current source geometry is unavailable.
- This is the first automatic producer-to-upload connection. It still requires full Gradle/Fabric build and in-game Zalith/MobileGlues validation; some terrain boundaries may be rejected if their extracted contours do not share matching endpoints.

Run `gradle stage43SmokeTest` for the CPU live-producer smoke test.


## Stage 44 — Seam Visibility & Runtime Validation

- Derives conservative world-space AABBs from every uploaded seam payload.
- Converts seam bounds into the same camera-relative coordinate space used by the terrain frustum.
- Submits seam draw calls only when their bounds intersect the current view frustum.
- Adds runtime counters for seam draw submissions and frustum rejections.
- Adds an eight-assertion CPU smoke test for bounds, camera-relative conversion, and invalid geometry.

This stage validates CPU visibility contracts; a full Fabric/Gradle build and in-game MobileGlues visual test are still required to confirm driver behavior and visible seam quality.


## Stage 45 — Seam Geometry Quality
- Seam payloads now preserve interpolated RGBA values from the fine and coarse source boundary meshes instead of painting every patch with a fixed debug tint.
- Zipper triangles are checked for finite, non-zero area; degenerate faces are dropped and remaining faces are normalized to a consistent winding.
- Added a Stage 45 smoke-test task covering source-color preservation and index/triangle validity.
- This is CPU-side geometry validation; visual blending and depth behavior still require a full Fabric build and an in-game Zalith/MobileGlues test.


## Stage 46 — Seam Integration & Stability
- Tracks fine and coarse source mesh revisions independently for every canonical seam pair.
- Assigns a monotonic per-pair upload revision only when one of the source meshes changes, avoiding collisions from summing source revision numbers.
- Exposes pending upload revisions and skips duplicate seam generation while an identical revision is already queued or uploaded.
- Clears per-pair revision state when seams become inactive, source meshes are unavailable, or the renderer is released.
- Adds CPU smoke tests for revision lifecycle and pending queue revision visibility.

Run `gradle stage46SmokeTest` for the Stage 46 CPU contract tests. Full Fabric compilation and in-game Zalith/MobileGlues validation are still required.


## Stage 47 — Seam Performance Optimization
- Prioritizes seam candidates by squared distance from the camera, with deterministic tie ordering.
- Caps CPU seam extraction/generation to eight candidates per neighbor refresh, preventing large visible LOD boundaries from monopolizing a refresh pass.
- Keeps all active seam keys in lifecycle tracking even when a candidate is deferred by the work cap; existing uploaded seams remain available until refreshed or invalidated.
- Adds a CPU smoke test for nearest-first ordering, stable ties, cap behavior, and invalid distance rejection.

Run `gradle stage47SmokeTest` for the Stage 47 scheduler contract test. Full Fabric compilation and in-game Zalith/MobileGlues performance validation are still required.


## Stage 48 — Asynchronous Seam Generation

Seam boundary extraction and patch construction now run on a dedicated single CPU worker with a bounded queue (16 requests). Mesh arrays are snapshotted before submission; workers never touch Minecraft renderer maps or OpenGL. Completed results are drained on the render thread, checked against the current seam revision, and then passed to the existing bounded GLES upload queue. Stale or failed results are ignored.

The worker is intentionally single-threaded for Android CPU/memory predictability. Queue saturation defers work rather than blocking the render callback.

## Stage 49 — Seam Visibility & Smart Scheduling

Adds a visibility- and lifecycle-aware seam candidate selector. Inactive and invisible pairs are skipped, duplicate pair keys are collapsed, and eligible candidates are ordered nearest-first under a per-refresh cap. See `docs/STAGE49_SEAM_SMART_SCHEDULING.md`.


## Stage 50 — Live Seam Runtime Scheduling

- Connects `LodSeamSmartScheduler` to the renderer's actual active LOD-neighbor refresh.
- Candidate visibility is derived from the current render frustum using camera-relative bounds for both neighboring nodes; a candidate is eligible when either neighbor intersects the frustum.
- Candidate lifecycle and source-pair revision are evaluated from the active fine/coarse mesh pair. The scheduler keeps the newest revision per seam key, then orders by camera distance and applies the existing cap of 8 CPU seam builds per refresh.
- CPU workers still receive immutable array snapshots; GPU upload and draw remain on the Minecraft render thread. Completed results and queued GPU uploads are both rejected when their revision is no longer current.
- Validation: Stage 49 scheduler regression test updated; Stage 50 runtime scheduling smoke test added. Full Gradle/Fabric build and Android in-game visual validation remain required.


## Stage 51 — Seam Geometry Validation & Visual Debugging Foundation

- Adds a CPU-only validation gate for the packed XYZ+RGBA seam payload before it can be accepted by the GPU mesh data object.
- Rejects malformed vertex strides, non-finite coordinates/colors, RGBA values outside 0..1, malformed triangle index counts, out-of-range indices, repeated-index triangles, and zero-area triangles.
- Exposes a compact validation report with vertex/triangle counts and estimated GPU byte size.
- This stage validates payload structure and triangle integrity; it does not prove the seam is spatially aligned with terrain or visibly rendered in Minecraft.
- Validation: Stage 51 smoke test added. Full Gradle/Fabric build and Android in-game visual validation remain required.

## Stage 52 — Seam Visual Debug Overlay

- Adds an optional orange wireframe AABB around each uploaded seam mesh.
- Toggle it in-game with **F7**; the keybind is also exposed through the normal Controls screen.
- Uses camera-relative line vertices and a reusable dynamic GLES buffer. The overlay is off by default and allocates no debug geometry until enabled.
- This visualizes seam mesh bounds only, not triangle topology or actual terrain contact. Full Gradle/Fabric and Zalith/MobileGlues device validation remain outstanding.
- Run `gradle stage52SmokeTest` for the CPU overlay geometry contract.

## Stage 53 — Seam Triangle Visualizer

The F7 seam debug overlay now includes cyan wireframe edges for the actual indexed seam triangles, in addition to orange AABB bounds. This helps inspect triangle topology and placement in-world; it does not by itself prove watertight terrain stitching.


## Stage 54 — Seam Crack Detection

- Adds a CPU-only point-to-polyline gap detector for seam boundary samples versus a terrain boundary.
- Reports sample count, matched/cracked counts, maximum gap, and mean gap against a configurable world-space tolerance.
- Supports open and closed terrain boundary polylines and rejects malformed/non-finite inputs.
- This is a diagnostic primitive; automatic live terrain-boundary extraction, in-world crack markers, and automatic seam repair are not included yet.
- Run `gradle stage54SmokeTest` for the Stage 54 geometry contract test. Full Fabric and Android device validation remain outstanding.


## Stage 55 — Live Terrain Boundary Extraction & Audit

- Added `LodSeamLiveBoundaryAudit` to capture fine/coarse boundary traces from the same indexed mesh snapshots used by the live seam producer.
- Records segment, vertex, byte, contour, open-contour, and closed-contour counts for both sides.
- `LodSeamLiveProducer.Result` now exposes the boundary audit, including for missing/mismatched-boundary outcomes, so failures can be diagnosed without touching GLES state.
- All extraction and audit work remains CPU-only and runs inside the existing asynchronous seam-build task.
- Run `gradle stage55SmokeTest` for the Stage 55 geometry contract test. Full Gradle/Fabric build and Android device validation are still required.

## Stage 56 — Seam Runtime Diagnostics

- Adds thread-safe counters for active seam candidates, CPU build scheduling/outcomes, stale-result rejection, upload handoff acceptance/rejection, GPU uploads, and mesh removals.
- Adds live gauges for pending/completed CPU work, pending GPU uploads, resident seam meshes, and estimated seam GPU bytes.
- Publishes the snapshot in the existing ten-second `[VoxyMobile] runtime status` log line, using actual renderer pipeline events.
- Run `gradle stage56SmokeTest` for the metrics snapshot contract. This is runtime observability, not proof of watertight visual seams; full Fabric and Android device validation remain outstanding.


## Stage 57 — Seam Failure Visualization

- Keeps the expected world-space boundary bounds for each scheduled seam build.
- When an asynchronous seam build returns a failure (including a worker exception), records a revision-scoped failure marker and its reason.
- With the existing F7 seam debug overlay enabled, failed candidate bounds are drawn as red wireframe boxes; successfully generated results clear their failure marker.
- Markers are removed when their seam key leaves the active set or the renderer is released.
- Run `gradle stage57SmokeTest` for the CPU overlay packing contract. The overlay has not yet been validated in a full Fabric build or on Zalith/MobileGlues.
