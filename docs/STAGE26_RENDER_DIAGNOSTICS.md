# Stage 26 — Render Submission Diagnostics

## Purpose

Stage 25 reported whether the client initializer, chunk capture, renderer callback and GPU mesh residency were active. It did not distinguish resident meshes from meshes actually submitted through `glDrawElements`. Stage 26 adds that distinction.

## New status fields

- `shaderReady`: custom shader program linked and both required uniforms were located.
- `leafDraws`: Leaf mesh draw submissions in the latest render callback.
- `parentDraws`: Parent mesh draw submissions in the latest render callback.
- `zeroDrawFrames`: cumulative render callbacks that submitted no Leaf or Parent draw calls.

## Reading the results

- `renderCallbacks=0`: WorldRenderEvents callback did not run.
- `shaderReady=false` with `rendererFailed=true`: inspect `lastError` for shader/GLES initialization failure.
- `capturedSections=0`: chunk ingestion/capture did not complete.
- `leafGpuMeshes=0` and `parentGpuMeshes=0`: no uploaded geometry is resident.
- GPU mesh counts above zero but both draw counts zero: selection/frustum/LOD traversal did not submit geometry.
- Draw counts above zero only prove draw commands were issued; they do not prove the GPU displayed visible pixels.

The status line is emitted at most every ten seconds. This stage adds observability only; it does not change the renderer's coordinate-space, depth, or shader behavior. Runtime verification still requires Minecraft 1.20.1 with Fabric on the target Zalith/MobileGlues device.
