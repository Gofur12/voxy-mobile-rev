# Stage 32 — Mesh Boundary Extraction

Stage 32 introduces `MeshBoundaryExtractor`, a dependency-free CPU helper for reading an ordered edge from packed `XYZ+RGBA` mesh vertices.

The caller specifies the boundary plane (normal axis + coordinate), tangent axis, remaining fixed axis/value, and tolerance. The extractor validates the packed layout and coordinates, filters points, sorts them along the tangent axis, and removes duplicate tangent positions.

Limitations: this extracts only vertices already present in the mesh. It does not interpolate intersections along quad edges, infer a neighbor relationship, call `LodSeamStitcher`, upload VBO/EBO data, or integrate with `GlesLodRenderer`. Those are subsequent integration tasks; this stage alone does not close visible terrain seams.
