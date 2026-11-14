# Stage 37 — Terrain mesh plane-boundary extraction

`MeshPlaneBoundaryExtractor` intersects indexed triangles in the renderer's existing seven-float XYZ+RGBA layout with an axis-aligned plane. Crossings are linearly interpolated, including RGBA. The caller supplies the overlap rectangle on the two remaining axes and a hard segment cap.

The output contains independent line segments (two packed vertices per segment). It deliberately skips fully coplanar triangles and does not claim to build a connected contour, clip partially overlapping segments to the rectangle, create seam triangles, or upload anything to GLES. A later stage must order/merge the segments and integrate the result with the active mesh lifecycle before a visible seam can be drawn.

Run `gradle stage37SmokeTest` with JDK 17 and Gradle 8.6.
