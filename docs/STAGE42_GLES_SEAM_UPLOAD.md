# Stage 42 — GLES seam upload and draw path

- Added `LodSeamGpuBuffer`, which allocates a VAO, VBO and EBO on the active Minecraft render thread.
- Uses the renderer's existing 7-float vertex contract: XYZ followed by RGBA; attribute locations 0 and 1 match the terrain shader.
- `GlesLodRenderer.enqueueSeamMesh` is the thread-safe CPU handoff. Actual GL calls happen only from `render` through `processSeamUploads`.
- Uploads are bounded to 512 KiB per frame after the first payload, replacing older revisions and deleting replaced buffers.
- Seam buffers are submitted through `glDrawElements(GL_TRIANGLES, ...)` after terrain nodes and are cleaned up on removal/release.

## Important limits

The live neighbor/seam generation path does not yet enqueue payloads automatically. This stage supplies a real GL upload/draw consumer, but no visible seam can be expected until a producer connects generated `LodSeamGpuMeshData` to `enqueueSeamMesh`. GLES execution on MobileGlues/Zalith and a full Gradle/Fabric build have not been validated in this environment.
