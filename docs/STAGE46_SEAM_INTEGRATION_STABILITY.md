# Stage 46 — Seam Integration & Stability

Stage 46 hardens the live seam lifecycle so repeated neighbor refreshes do not regenerate and enqueue identical payloads.

- Adds `LodSeamRevisionTracker`, which tracks the fine and coarse source revisions independently for each canonical seam key.
- Gives each changed source pair a monotonically increasing upload revision. This avoids revision collisions from adding the two source revisions together.
- Keeps the same upload revision while both source revisions are unchanged.
- Adds `LodSeamUploadQueue.pendingRevision(key)` so the live producer can detect an identical payload already waiting for render-thread upload.
- Skips CPU seam regeneration when either the current GPU buffer or pending upload already matches the current source-pair revision.
- Removes revision-tracker state when a seam pair becomes inactive or its source geometry cannot be used, and clears it during renderer teardown.
- Extends CPU smoke coverage for revision lifecycle and pending queue revision queries.

The queue remains bounded and GLES allocation/deletion remains in the render callback. This stage does not establish full Fabric build success or visual correctness on Zalith/MobileGlues; those still require GitHub Actions and device testing.
