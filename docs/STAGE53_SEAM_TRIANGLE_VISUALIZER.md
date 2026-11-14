# Stage 53 — Seam Triangle Visualizer

Adds a cyan wireframe diagnostic for the actual indexed triangles in each uploaded seam mesh. The render-thread GPU buffer retains a compact CPU-side line payload so the debug overlay can show triangle topology without reading data back from GL.

- The existing F7 seam debug toggle now draws orange seam AABBs and cyan triangle edges.
- Triangle lines use the same camera-relative coordinates as the renderer.
- Invalid index references are rejected by the visualizer.
- Debug geometry is only built when a seam mesh is uploaded and drawn only while the debug overlay is enabled.

This is a topology diagnostic, not proof that seams are watertight or visually blended with terrain. Full Fabric build and Android/MobileGlues visual validation are still required.
