# Stage 54 — Seam Crack Detection

Adds a CPU-only diagnostic that compares seam boundary sample points with a terrain boundary polyline. It computes point-to-segment distances (not merely nearest-vertex distances), reports matched and cracked sample counts, maximum gap, and mean gap, and supports open or closed terrain boundary polylines.

The tolerance is supplied in world-coordinate units. This is a diagnostic primitive: the caller must provide seam boundary samples and the corresponding terrain boundary in the same world coordinate space and with consistent axis/orientation. It does not yet automatically extract the live terrain boundary from the Voxy renderer, draw crack markers in-world, or repair geometry.

Run `gradle stage54SmokeTest` for the CPU contract test. Full Gradle/Fabric build and Zalith + MobileGlues validation remain required.
