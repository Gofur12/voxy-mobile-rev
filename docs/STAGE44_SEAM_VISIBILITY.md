# Stage 44 — Seam Visibility & Runtime Validation

## Changes

- `LodSeamBounds` derives finite world-space min/max bounds from packed XYZ+RGBA vertices.
- `LodSeamGpuBuffer` retains those bounds and exposes a frustum-gated draw method.
- `GlesLodRenderer` tests seam bounds in camera-relative coordinates before issuing seam draw calls.
- Runtime diagnostics now report `seamDraws` and `seamFrustumRejected` for the latest frame.

## Why

Stage 43 generated and uploaded seam payloads, but Stage 42/43 drew every resident seam unconditionally. This stage prevents off-screen seam patches from consuming draw calls and makes visibility decisions observable.

## Validation limits

The CPU bounds test does not prove pixels appear on screen. The GPU upload class and full Minecraft renderer still require a Gradle/Fabric build and a Zalith + MobileGlues device test. Seam material/color and depth interaction remain subjects for that visual test.
