# Stage 63 — Independent LOD distance

## Setting
The client creates `config/voxymobile.properties` with `lodDistanceBlocks=2048` by default. Edit the value and restart Minecraft. Accepted range: 256–8192 blocks. This setting is independent of vanilla render distance.

## Renderer integration
- Leaf mesh builds are limited to the configured LOD radius.
- Parent mesh scheduling uses nearest-point distance to each node AABB.
- Hierarchical traversal excludes nodes outside the radius.
- Leaf visibility uses the same configured radius.
- Existing overloads preserve previous unlimited traversal behavior for other callers.

## Limits
This is a render/selection range, not a terrain generator. It can only display snapshots already captured and retained by the client. Stage 62's bounded snapshot capacity and GPU budget still limit how much explored terrain is simultaneously available.

## Validation
Stage 63 smoke test covers radius exclusion/inclusion. CI validates compilation; Android testing is still required for real-world far-distance coverage and performance.
