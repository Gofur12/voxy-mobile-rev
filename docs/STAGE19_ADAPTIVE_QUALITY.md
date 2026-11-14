# Stage 19 — Adaptive LOD Quality

## Runtime policy

`AdaptiveLodQualityController` is a dependency-free policy object fed by measured render-callback intervals. It uses an exponential moving average (alpha 0.12), enters a lower-quality tier after eight sustained samples above 112% of the target frame time, and recovers one tier after ninety sustained samples below 78% of target. Samples in the middle band reset both counters. Invalid or pause-sized samples are ignored.

| Tier | LOD distance scale | New mesh requests/frame | Completed uploads/frame |
|---|---:|---:|---:|
| 0 | 1.00 | 4 | 3 |
| 1 | 0.90 | 3 | 2 |
| 2 | 0.78 | 2 | 1 |
| 3 | 0.65 | 1 | 1 |

The movement-speed policy from Stage 11 remains active and composes with the quality-tier scale. The renderer caps both leaf and parent mesh request submissions per frame and uses the tier-aware upload budget for leaf and parent uploads. Existing worker queues remain bounded and OpenGL operations remain on the render callback thread.

## Measurement caveat

The current signal is the interval between renderer callbacks. It includes frame pacing and potentially vsync; it is not a GPU timer query and cannot distinguish CPU, GPU, or external scheduling stalls. This avoids introducing unsupported GLES extensions on MobileGlues, but thresholds must be profiled on the target device before tuning.

## Tests

`AdaptiveLodQualityControllerSmokeTest` verifies invalid sample handling, sustained-load downgrade, gradual recovery, spike resistance, reset, and integration with the streaming LOD/build-budget policy.
