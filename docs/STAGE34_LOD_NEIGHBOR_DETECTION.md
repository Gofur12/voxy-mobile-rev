# Stage 34 — LOD neighbor detection

`LodNeighborDetector` detects horizontal adjacency between axis-aligned terrain regions in world coordinates. The result records which sides touch, the shared boundary plane, the positive overlap interval, and the LOD difference. It rejects separated regions and corner-only contacts. Equal-LOD adjacency is reported but does not require a seam patch.

This is a CPU utility, not live integration: the renderer does not yet feed its selected leaf/parent nodes into this detector, and no VBO/EBO upload or draw call is added in this stage.

Run `gradle stage34SmokeTest`.
