# Stage 78 — LOD-aware GPU mesh eviction

- GPU allocation eviction now measures distance to each mesh region's AABB instead of its center.
- A bounded level bias makes fine leaf meshes more likely to be evicted before coarse parent meshes at comparable distance.
- Coarse parent meshes remain useful fallback coverage while fine meshes stream or are re-uploaded; substantially farther parent meshes can still be evicted.
- Existing shared GPU byte budget, hysteresis, protected just-uploaded section, and render-thread-only deletion remain in place.
- Added pure policy smoke tests for equal-distance detail preference, distance override, camera-local retention, and invalid inputs.
- This changes eviction priority only; it does not change world data availability or claim Android GPU profiling.
