# Stage 28 — Terrain draw-path audit

The renderer previously exposed resident mesh counts and draw-call counts, but not the reasons a selected node failed to reach a draw call. This stage adds `RenderFrameAudit` and integrates it into the existing hierarchical draw loop.

Counters per rendered frame:
- `frameCandidates`: selected hierarchy nodes inspected by draw.
- `frustumRejected`: nodes rejected by camera-relative frustum bounds or leaf visibility.
- `missingMeshes`: selected leaf/parent geometry absent from GPU maps.
- `staleParents`: parent GPU geometry revision no longer matches the node.
- Existing `leafDraws` and `parentDraws`: submitted indexed draw calls.

Interpretation: if candidates are zero, traversal/selection supplied nothing; if candidates are nonzero and rejections dominate, inspect frustum/camera transforms; missing meshes point to build/upload/cache flow; stale parents point to revision invalidation; draw counts above zero prove submission only, not visible pixels. The audit does not read pixels or claim a visual pass.
