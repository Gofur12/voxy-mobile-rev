# Stage 79 — Configurable GPU memory budget

- Added a Rendering-page slider for GLES mesh allocations, 32–256 MiB in 16 MiB increments; default 64 MiB.
- Persists as `gpuMemoryBudgetMb` in `config/voxymobile.properties` and applies live without restarting Minecraft.
- Lowering the budget activates the existing hysteretic eviction policy on the next render frame, even without a mesh upload.
- The limit is the renderer's tracked mesh-allocation target, not total device GPU memory.
