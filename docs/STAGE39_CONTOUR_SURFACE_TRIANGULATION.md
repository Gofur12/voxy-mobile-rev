# Stage 39 — Contour Surface Triangulation

`ContourSurfaceTriangulator` triangulates a simple, planar, closed XYZ+RGBA contour using ear clipping on its dominant-axis projection. It returns triangle indices referencing the original packed contour vertices and a normalized Newell normal. It rejects open, non-finite, degenerate, non-planar, or non-simple input.

This is CPU geometry preparation only. It is not connected to the live seam generator, GLES VBO/EBO upload, or draw submission. Concave simple polygons are supported; holes and arbitrary non-planar boundaries are not.

Smoke test: `stage39ContourSurfaceTriangulationSmokeTest`.
