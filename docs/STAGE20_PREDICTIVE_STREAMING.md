# Stage 20 — Predictive Streaming & Camera Movement Optimization

Target: Minecraft Java 1.20.1 / Fabric / Java 17; Android Zalith Launcher + MobileGlues GLES 3.x.

## What changed

- Adds a CPU-only directional priority policy that combines the existing short-horizon predicted camera position with camera view direction and smoothed travel velocity.
- Sections and parent LOD nodes in front of the view/travel direction receive a bounded priority advantage; work behind the camera is deprioritized without replacing distance as the main ordering signal.
- Leaf and parent schedulers refresh priorities for jobs that are still queued when the camera turns or changes direction. In-flight work is not interrupted.
- Existing bounded queues, worker-thread meshing, revision validation, render-thread-only GLES uploads, and parent fallback selection remain intact.
- Duplicate outstanding requests no longer consume the per-frame *new request* budget, while their queued priority can still be refreshed.

## Policy details

- Directional multiplier is bounded to 0.78–1.22 around the predicted-distance score.
- View direction is blended with normalized movement direction up to 55%; movement influence grows with camera speed and is zero while stationary.
- Invalid direction vectors fall back to distance-only priority. Invalid priority scores are safely deprioritized.
- The policy uses ordinary CPU math and existing GLES-compatible rendering; no compute shaders, SSBOs, or Hi-Z path is introduced.

## Validation and limits

- `PredictiveStreamingControllerSmokeTest` covers forward/behind ordering, movement-direction influence, invalid vectors/scores, and bounded distance-aware bias.
- The policy is deterministic and dependency-free, so it can be tested with Java 17 without Minecraft or an Android GPU.
- Camera direction is sampled from Minecraft's camera rotation. Actual perceived pop-in and queue throughput still require profiling in Zalith on MobileGlues/Adreno 640.
- The scheduler reprioritizes queued work only; a worker task already executing is allowed to finish. No task cancellation or speculative chunk loading is performed.
- Full Gradle/Fabric build and in-game runtime tests were not run in this environment.
