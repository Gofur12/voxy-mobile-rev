# Stage 76 — GPU-ready hierarchical fallback

- Distance traversal now accepts a renderability predicate in addition to logical node geometry state.
- The GLES renderer validates that leaf GPU meshes still match their source snapshot and that parent GPU meshes match the node content revision before selection.
- When descendants are logically ready but their GPU resources are absent/evicted, a current renderable ancestor is selected as fallback instead of leaving a hole.
- Existing callers retain the original overload; new smoke coverage simulates GPU eviction by making only the parent renderable.
- CI validates selection policy, not device-side rendering. Android visual testing remains required.
