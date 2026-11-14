# Stage 50 — Live Seam Runtime Scheduling

## Runtime changes

The renderer now feeds the Stage 49 smart scheduler from the same selected-neighbor batch used by the live LOD render pass. Each candidate is checked against the current frustum using camera-relative bounds for both adjacent nodes. Candidates with neither node visible are not sent to CPU seam generation.

The active fine/coarse mesh content revisions are converted to a stable seam revision. Duplicate seam keys retain the newest revision; equal revisions use the nearer candidate. The selected list is nearest-first and capped at eight seam builds per ten-frame neighbor refresh.

Worker threads only process cloned CPU mesh arrays. Completed results are checked against the current revision before entering the render-thread upload queue, and the revision is checked again when the render thread consumes that upload queue to prevent an already-queued stale payload from replacing newer geometry. GLES allocation, upload, and draw remain render-thread operations.

## Validation and limitations

The Stage 49 and Stage 50 CPU smoke tests cover candidate visibility/lifecycle filtering, newest-revision deduplication, deterministic distance ordering, and work caps. They do not validate actual GLES output. A complete Gradle/Fabric build and an Android Zalith + MobileGlues in-game test are still needed to confirm visible seam geometry and performance.
