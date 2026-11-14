# Stage 45 — Seam Geometry Quality

## Changes
- `LodSeamLiveProducer` now keeps packed XYZ+RGBA boundary contours intact through pair matching. Reversing an edge also reverses its color data.
- `LodSeamPatchData.buildPackedEdges` builds zipper indices from XYZ coordinates while retaining the original boundary RGBA attributes.
- The zipper output drops zero-area triangles and flips faces whose winding opposes the first valid face.
- Invalid packed colors and non-finite vertex values are rejected before upload.

## Validation
The live producer smoke test now checks that fine/coarse colors survive, indices remain in range, and emitted triangles are non-degenerate.

## Limits
This does not yet perform material-aware color blending, depth-bias tuning, or visual seam validation. A real Minecraft run is still required to confirm appearance on MobileGlues GLES.
