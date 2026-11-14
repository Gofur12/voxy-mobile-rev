# Stage 52 — Seam Visual Debug Overlay

- Adds an opt-in in-world wireframe AABB overlay for uploaded seam GPU meshes.
- Press **F7** to toggle the overlay. Orange lines show the world-space bounds of each resident seam mesh, translated into the renderer's camera-relative coordinate space.
- The overlay reuses the existing GLES terrain shader and a reusable dynamic VAO/VBO; line vertices are generated only while the overlay is enabled.
- It is a bounds diagnostic, not a visualization of the seam triangles themselves and not proof that seam surfaces are watertight.
- The CPU builder is covered by `stage52SmokeTest`. Full Fabric compilation and Android/MobileGlues visual verification remain necessary.
