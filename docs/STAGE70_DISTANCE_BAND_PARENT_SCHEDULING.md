# Stage 70 — distance-band parent mesh scheduling

- Parent mesh generation is now gated by the hierarchical selector's active distance band.
- The renderer still tracks every in-range parent as live and retains already-ready parent meshes as fallback geometry.
- Missing parent levels outside the current band are deferred instead of being meshed eagerly.
- This reduces CPU aggregation, snapshot copying, and worker queue pressure across the 4096–8192 block LOD radius.
- Coarse-first priority, bounded worker scheduling, revision checks, and render-thread-only GLES uploads remain unchanged.

Validation: Stage 70 smoke coverage checks near/far build eligibility and avoids rebuilding current geometry. Android visual performance still requires an on-device test.
