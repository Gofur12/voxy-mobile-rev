# Stage 43 — Live LOD seam generation

Stage 43 connects the active LOD selection neighbor batch to seam payload generation.

1. Leaf and parent GPU mesh records retain the CPU-side XYZ+RGBA vertex arrays and indices used for their upload.
2. Every ten render frames, the renderer takes the same selected-node snapshot used by the terrain draw pass and resolves each unequal-LOD neighbor candidate to its current mesh payloads.
3. `LodSeamLiveProducer` intersects each indexed mesh with the shared X/Z boundary plane, clips the trace to the shared overlap, stitches segments into open contours, and pairs fine/coarse contours only when both endpoints match (reversing one contour when required).
4. A matched pair is converted to a zipper patch and immutable `LodSeamGpuMeshData`; the renderer enqueues it for Stage 42's render-thread GLES upload path.
5. Missing meshes, missing traces, or unmatched endpoints are rejected. Existing seam resources are removed when their candidate pair disappears or its current source geometry is unavailable.

The producer deliberately does not invent endpoint extensions or bridge unrelated contours. The result can therefore be zero generated seams on terrain whose boundary traces are discontinuous or do not share endpoints. This is a conservative integration milestone, not proof of visually correct seams on device. Full Gradle/Fabric compilation and Zalith + MobileGlues testing remain required.

Run `gradle stage43SmokeTest` for the focused CPU geometry test.
