# Stage 61 — Live section data synchronization

## Purpose
Keep the LOD snapshot store synchronized after client-side block changes, instead of treating chunk load as the only terrain data source.

## Changes
- Inject a client-only hook after successful Minecraft 1.20.1 ClientWorld#setBlockState calls.
- Map changed block positions to their owning chunk section and enqueue a refresh.
- Coalesce repeated edits by section position in an insertion-ordered queue capped at 8,192 entries.
- Re-read the current section on the client tick instead of retaining a mutable section reference.
- Remove the snapshot and its LOD-tree leaf when a section becomes empty.
- Keep chunk-load ingestion and chunk-unload cleanup.

## Important limitation
This updates terrain data for chunks already delivered to the client. It does not increase server chunk delivery or exceed vanilla chunk loading distance. Independent far-distance terrain acquisition/streaming is a separate core milestone.

## Validation
CI compilation and smoke tests cover source integration and snapshot removal. Runtime verification still requires editing terrain in a Minecraft world and confirming the LOD follows the edits.
