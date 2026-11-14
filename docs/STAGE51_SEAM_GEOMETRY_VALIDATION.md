# Stage 51 — Seam Geometry Validation & Visual Debugging Foundation

`LodSeamGeometryValidator` is a CPU-only validation gate for the seven-float XYZ+RGBA vertex layout and indexed triangle payload.

It checks finite vertex attributes, normalized RGBA channels, triangle-aligned indices, index bounds, repeated indices, and near-zero-area triangles. Its report includes a stable status code, counts, and estimated VBO/EBO byte size. `LodSeamGpuMeshData` now rejects payloads that fail this gate, preventing malformed geometry from reaching the GLES upload queue.

This is structural validation, not a spatial correctness proof: it does not verify that the seam lies exactly on the shared terrain boundary, has the intended winding relative to terrain normals, or appears in the game. Those require runtime geometry inspection and an Android/Zalith visual test.
