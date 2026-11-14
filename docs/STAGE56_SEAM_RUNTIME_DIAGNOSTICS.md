# Stage 56 — Seam Runtime Diagnostics

Stage 56 exposes live seam-pipeline health in the existing periodic `[VoxyMobile] runtime status` line.

The snapshot reports cumulative candidate observations, scheduled CPU builds, successful/failed build results, stale result drops, accepted/rejected CPU-to-GPU handoffs, completed GPU uploads, and removed meshes. It also reports current active candidates, pending/completed CPU jobs, pending uploads, resident seam GPU meshes, and their estimated GPU byte footprint.

The renderer updates these metrics at the actual scheduling, result-consumption, queue-handoff, upload, and mesh-removal points. Worker threads remain CPU-only; GLES allocation and upload still occur in the render callback. The metrics are diagnostic counters, not proof that seam triangles are visually watertight or that a complete Fabric build succeeds.

Run `gradle stage56SmokeTest` to verify the metrics snapshot and formatting contract. Full Gradle/Fabric and Zalith + MobileGlues device validation remain required.
