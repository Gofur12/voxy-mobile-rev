# Stage 60 — Projected terrain seam zipper

## Problem
Live terrain boundary contours can share the same horizontal edge extent while their endpoint heights differ. The previous producer matched full XYZ endpoints and the legacy zipper rejected those pairs, so a valid fine/coarse boundary pair could never produce a patch.

## Change
- Match open contour endpoints on the horizontal progress axis of the shared LOD boundary.
- Reverse the coarse contour only when its projected direction is opposite.
- Triangulate by normalized projected coordinate rather than point count.
- Preserve the legacy full-XYZ zipper for callers that truly require identical endpoints.
- Keep source RGBA values and the existing degenerate-triangle filter.

## Validation
Stage60 reuses the live producer smoke test with intentionally different fine/coarse endpoint heights and matching horizontal extent. This validates the CPU seam payload path; it does not prove Android GPU/runtime correctness.