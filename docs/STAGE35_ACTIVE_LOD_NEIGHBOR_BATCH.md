# Stage 35 — Active LOD neighbor batch bridge

`LodNeighborBatch.build(activeRegions, maxCandidates)` consumes one explicit list of selected horizontal LOD regions. It checks each unordered pair once using `LodNeighborDetector`, preserves deterministic input-index ordering, and returns immutable candidates. The result includes region count, neighbor count, LOD-mismatch seam-candidate count, and a truncation flag.

The candidate cap limits retained result memory. If another adjacent pair is encountered after the cap is reached, scanning stops and `truncated()` is true. A cap of zero therefore safely reports truncation when at least one neighbor exists.

## Scope boundary

This is a tested CPU bridge, not live renderer wiring: no active node collection is currently passed by `GlesLodRenderer`. It does not infer mesh edge queries or create seam geometry. A terrain boundary is a vertical surface whose height varies; the existing Stage 32 extractor selects a one-dimensional line with a fixed coordinate, so it cannot yet extract a general terrain side surface. GPU upload and draw submission remain future work.

Run `gradle stage35SmokeTest` with the project Gradle/JDK setup.
