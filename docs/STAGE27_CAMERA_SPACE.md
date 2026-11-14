# Stage 27 — Camera-space consistency

## Bug addressed

Leaf streaming and `SectionVisibilityPipeline` tested camera-relative AABBs, matching the vertex shader's `Position - CameraPosition` input. Hierarchical draw selection instead tested parent-node AABBs in absolute world coordinates against the same view frustum. As a result, parent nodes could be incorrectly rejected or accepted depending on the camera's world position.

## Change

`CameraRelativeAabb` is now the shared conversion utility for section and parent-node bounds. Draw-time parent culling converts the node's world-space origin/span relative to the current camera before testing the frustum. The shader continues to subtract camera position exactly once from absolute world-space mesh vertices.

## Validation

`CameraRelativeAabbSmokeTest` checks section and parent bounds at positive and negative world coordinates, non-zero camera coordinates, frustum visibility, and invalid spans. A successful CPU smoke test does not substitute for compiling against Fabric/Loom or an on-device visual test.
