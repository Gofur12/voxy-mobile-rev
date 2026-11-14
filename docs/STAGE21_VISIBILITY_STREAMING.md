# Stage 21 — Visibility-Aware Streaming

## Goal
Prioritize CPU mesh generation and parent-node generation using the current camera frustum and conservative section enclosure information, while retaining Stage 20 movement/view-direction priority.

## Behavior
- Leaf sections are tested against the current render frustum before scheduling.
- Leaf sections fully enclosed by six neighboring sections with fully occupied facing boundary planes receive a lower scheduling priority. This uses the existing conservative opaque-boundary assumption.
- Parent nodes are tested against the same camera frustum and off-screen nodes are deprioritized.
- Visibility is a weighting, not a hard rejection. Off-screen or enclosed work remains eligible so quick camera turns and future visibility changes do not permanently starve it.
- The score is combined with Stage 20 predicted-distance and direction priority; lower values are processed first.
- Existing revision checks, bounded queues, per-frame build budgets, and parent fallback selection remain in place.

## Limitations
- This is frustum-aware scheduling, not pixel-perfect GPU occlusion or Hi-Z. No compute shader, SSBO, or depth readback is used.
- Enclosure detection assumes non-air blocks are opaque, matching the current mesh color path. Transparent block/material metadata is not available yet; if transparency is introduced, enclosure penalties must be disabled or made material-aware.
- Frustum visibility is a per-frame CPU test. It does not guarantee the GPU will draw the section, and off-screen work is not discarded.
- Runtime behavior still requires a full Fabric build and testing in Minecraft 1.20.1 on Zalith + MobileGlues GLES 3.2.

## Test
`stage21SmokeTest` checks visible/off-screen ordering, conservative enclosure weighting, parent-node weighting, and invalid-score handling.
