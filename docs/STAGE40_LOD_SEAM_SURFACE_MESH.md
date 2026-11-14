# Stage 40 — LOD Seam Surface Mesh

Stage 40 connects the packed closed-contour representation to the Stage 39 triangulator. `LodSeamSurfaceMesh.build` returns an indexed XYZ+RGBA mesh, removes a duplicated closing vertex, preserves RGBA values, validates indices, and exposes normal and byte-size metadata.

The implementation is CPU-only. It does not allocate GLES objects, upload VBO/EBO buffers, or submit draw calls. It accepts only the simple, planar, closed contours supported by `ContourSurfaceTriangulator`; open, self-intersecting, degenerate, or non-planar contours remain rejected.

Validation: `LodSeamSurfaceMeshSmokeTest` covers triangulation, duplicate endpoint removal, RGBA preservation, index bounds through the builder, byte size, defensive copies, normal, and invalid inputs.
