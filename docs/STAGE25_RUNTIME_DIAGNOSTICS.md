# Stage 25 — Runtime Activation Diagnostics

## Purpose
The user reported that Stage 20.1 appeared to have no effect in Minecraft. Before adding further visual optimizations, Stage 25 instruments the runtime path so the next `latest.log` can identify where activity stops.

## Log markers
- Startup: `[VoxyMobile] client initializer active...`
- Periodic status (at most every 10 seconds): chunk load/unload counts, queued and captured sections, world render callback count, Leaf/Parent GPU mesh counts, and renderer failure state.
- Renderer exception: `[VoxyMobile] GLES renderer disabled: ...` plus a recorded failure state.

## Reading the counters
- No startup marker: mod entrypoint did not initialize or the wrong jar/instance was loaded.
- Startup marker but zero chunk loads: chunk event path did not fire or the player has not entered a world.
- Chunk loads but no captured sections: pending chunk identity/world validation or tick ingestion needs investigation.
- Captured sections but zero render callbacks: world render event registration/path needs investigation.
- Render callbacks but zero meshes: inspect scheduler, mesh-build errors, visibility/LOD selection and section content.
- Renderer failed: use the exception printed in `latest.log`; this is not silently treated as success.

## Scope and limitations
This is observability, not a visual fix. It adds no HUD and no config. The periodic status is emitted to the Minecraft client log. Counts are diagnostic counters, not performance benchmarks or proof of GPU rendering correctness. Full Gradle/Fabric build and Zalith/MobileGlues runtime test remain required.
