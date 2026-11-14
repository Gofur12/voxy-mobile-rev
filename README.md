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


## Stage 20.2 API compatibility patch

Stage 20.2 fixes two Minecraft 1.20.1 Yarn API calls in the client chunk ingestion adapter: loaded-chunk identity is checked without forcing chunk generation, and block-state raw IDs use `Block.getRawIdFromState`. See `docs/STAGE20_2_MINECRAFT_API_FIX.md`.
