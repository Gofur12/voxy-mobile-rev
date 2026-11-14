# Stage 24 — GPU Upload Pipeline Optimization

## Goal
Bound the amount of vertex/index payload submitted to GLES buffers in one render frame. Leaf and parent mesh uploads share one budget so their independent queues cannot each consume a full allowance.

## Policy
- Default budget: 2 MiB per frame.
- GPU memory pressure level 1: 1.5 MiB per frame.
- Pressure level 2: 1 MiB per frame.
- Pressure level 3: 512 KiB per frame.
- Byte estimates include vertex floats and index integers, matching the two buffer payloads submitted by the renderer.
- One mesh larger than the frame budget may upload if no other payload has been submitted that frame. This prevents a large mesh from being deferred forever; no additional upload is admitted that frame.
- Leaf payloads that do not fit remain in a render-thread deferred slot. Parent payloads are returned to the bounded parent upload queue.

## Integration
`GlesLodRenderer` begins the budget once per render callback and shares it across leaf and parent uploads. All OpenGL allocation and buffer submission continue to run on the Minecraft render thread. CPU mesh generation remains asynchronous.

## Validation
`GpuUploadBudgetControllerSmokeTest` covers normal accounting, pressure scaling, oversized-mesh starvation avoidance, per-frame reset, zero-byte payloads, and invalid input rejection.

## Limitations
This controls bytes submitted per render callback; it does not combine multiple meshes into a single GL buffer, use persistent mapping, issue GPU fences, or report actual driver VRAM/DMA behavior. Those techniques require separate capability checks and device testing, especially on GLES 3.2 mobile drivers.
