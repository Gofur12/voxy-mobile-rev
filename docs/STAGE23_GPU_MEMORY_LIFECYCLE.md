# Stage 23 — GPU Memory & Mesh Lifecycle Optimization

Stage 23 adds a CPU-only `GpuMemoryBudgetController` and integrates its hysteretic policy into the GLES renderer's shared leaf/parent mesh eviction path.

- Default shared GPU allocation budget remains 64 MiB.
- Eviction begins above 90% of the budget.
- Eviction continues until usage reaches 78%, reducing repeated churn near the boundary.
- Existing distance-based farthest-mesh selection and protected newly-uploaded position remain in place.
- `gpuBytesAllocated()` and `gpuMemoryPressureLevel()` expose lightweight diagnostics.
- All GL allocation, deletion and upload calls remain in the render callback; the policy itself makes no GL calls.

This is an estimate based on submitted vertex/index buffer sizes, not driver-reported physical VRAM. VAO/VBO/EBO overhead, driver copies and transient upload buffers are not fully represented. Runtime memory behavior still requires testing on the target Android GPU.

This stage also carries forward the Stage 20.2 Minecraft 1.20.1 Yarn fixes: non-loading `ChunkStatus.FULL` identity validation and `Block.getRawIdFromState`.

Run: `gradle build stage11SmokeTest stage12SmokeTest stage13SmokeTest stage14SmokeTest stage15SmokeTest stage16SmokeTest stage17SmokeTest stage18SmokeTest stage19SmokeTest stage20SmokeTest stage21SmokeTest stage22SmokeTest stage23SmokeTest --no-daemon`.
