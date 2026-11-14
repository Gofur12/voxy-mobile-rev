# Stage 57 — Seam Failure Visualization

Stage 57 adds an in-world failure overlay for asynchronous seam generation.

- The renderer records expected world-space seam bounds when scheduling a build.
- A failed producer result creates a revision-scoped `LodSeamFailureMarker` with its status; an uncaught worker exception is labeled `worker-exception`.
- The existing F7 seam debug toggle controls both the successful seam diagnostics and red failure-bound wireframes.
- A successful result clears the failure marker. Removing a seam key and releasing the renderer clear associated marker state.
- The overlay is CPU-packed camera-relative XYZ+RGBA line data and drawn on the render thread using the existing debug VAO/VBO.

The smoke test validates packing, camera-relative translation, red color, empty inputs, and marker metadata. It does not prove correct GLES state on device or that every failed build has a marker (for example, jobs rejected before submission have no completed result).
