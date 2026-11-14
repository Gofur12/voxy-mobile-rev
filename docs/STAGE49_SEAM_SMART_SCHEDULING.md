# Stage 49 — Seam Visibility & Smart Scheduling

Adds `LodSeamSmartScheduler`, a deterministic CPU-side selector for seam build candidates.

- Drops candidates whose neighbor pair is no longer active.
- Drops candidates outside the supplied visibility result.
- Collapses duplicate region-pair keys so one pair is scheduled once per selection pass.
- Orders remaining work nearest-first with region IDs as stable tie-breakers.
- Enforces a caller-provided per-refresh work cap and rejects invalid distances.

The scheduler is a reusable policy component. This stage does not claim that all renderer candidate sources have been wired to it, nor does it claim visual culling has been validated on-device. Existing async build and render-thread upload boundaries remain unchanged.

Run `gradle stage49SmokeTest --no-daemon` with JDK 17 and Gradle 8.6.
