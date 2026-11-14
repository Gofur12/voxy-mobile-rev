# Stage 20.2 — Minecraft 1.20.1 API compatibility fix

This patch is based on the Stage 20.1 source and fixes two Yarn 1.20.1 API mismatches in `VoxyMobileClient`.

- Replaces `WorldChunk.isLoaded()` with a non-loading identity check through `ClientWorld.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false)`. This avoids requesting or generating a chunk while processing the pending snapshot queue and rejects stale chunk references.
- Replaces `Block.getRawId(BlockState)` with the Yarn 1.20.1 method `Block.getRawIdFromState(BlockState)`.

No LOD, predictive scheduling, GLES, or queue policy was changed.

## Verification status

The source was checked against Yarn 1.20.1 API documentation. ZIP integrity and metadata checks are performed for this package. A full Gradle/Fabric build and in-game Zalith/MobileGlues test must still be run in GitHub Actions / the target device.
