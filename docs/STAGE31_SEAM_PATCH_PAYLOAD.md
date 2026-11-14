# Stage 31 — GLES-ready seam patch payload

`LodSeamPatchData` turns the Stage 30 zipper indices and two ordered XYZ boundary edges into the same interleaved vertex format used by `GlesLodRenderer`: XYZ followed by RGBA (seven floats). It validates normalized finite color channels, delegates endpoint/direction/finite-coordinate checks to `LodSeamStitcher`, and reports vertex/index/triangle counts plus exact CPU payload bytes. Array accessors return defensive copies.

The result is suitable as input to the existing VBO/EBO upload pattern, but this stage does not issue OpenGL calls and does not yet extract adjacent boundaries from live leaf/parent meshes. Therefore it does not claim visible in-game seam closure.

Focused test: `gradle stage31SmokeTest`.
