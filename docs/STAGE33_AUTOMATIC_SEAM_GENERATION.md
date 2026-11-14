# Stage 33 — Automatic seam patch generation

## What changed

`LodSeamPatchGenerator` now wires together the three previous CPU primitives:

1. `MeshBoundaryExtractor` extracts ordered XYZ edge points from packed XYZ+RGBA mesh arrays.
2. `LodSeamStitcher` triangulates the fine/coarse polylines using zipper progression.
3. `LodSeamPatchData` packs the vertices as XYZ+RGBA and produces the index payload.

The caller supplies an `EdgeQuery` for each mesh: normal axis/plane, tangent axis, fixed axis/value, and tolerance. Both meshes must already use the same world coordinate system, both queries must select the intended shared edge, and the extracted polylines must have matching endpoints in the same direction.

## Deliberate limitations

This stage does not discover neighboring sections automatically, infer edge topology, interpolate missing boundary vertices, resolve reversed edge winding, upload GPU buffers, or submit draw calls. It is an end-to-end CPU payload generator, not yet live renderer integration. Incorrect edge query descriptions can select an unintended line; callers must supply correct topology metadata.

## Validation

`LodSeamPatchGeneratorSmokeTest` covers successful fine/coarse extraction and payload generation, triangle/index counts, byte-size calculation, mismatched endpoints, empty extraction, and null input. Run `gradle stage33SmokeTest`.
