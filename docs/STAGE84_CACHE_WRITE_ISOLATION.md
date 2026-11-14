# Stage 84 — Cache write isolation during restore

The persistent cache worker now uses independent bounded executors for cache restore and cache writes/removals. A restore that is applying backpressure to the client-side 128-section handoff no longer occupies the only worker responsible for saving newly captured live terrain.

The streaming restore method also no longer holds the cache object's monitor while invoking the consumer. Disk writes and removals can therefore enter their synchronized critical sections while the restore consumer is waiting for handoff capacity. Individual section files continue to use atomic replacement.

The Stage 84 smoke test fills the restore handoff to its 128-entry limit, submits a live section save, and asserts that the new cache file appears before the restore queue is drained. It then verifies that the original nearest-first restore still delivers all expected entries.

This stage improves persistence responsiveness; it does not create terrain data that Minecraft has never delivered and has not been visually verified on Android.
