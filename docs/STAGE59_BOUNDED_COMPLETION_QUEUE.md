# Stage 59 — Bounded completed seam queue

## Problem
The CPU seam builder already bounded its worker queue, but completed mesh payloads were held in an unbounded concurrent queue. If the render thread paused or consumed results slowly, finished payloads could accumulate and retain large vertex arrays.

## Change
- Replace the completed-result queue with an `ArrayBlockingQueue` using the configured capacity.
- Publish completed results with non-blocking `offer`; never block a worker waiting for the render thread.
- Count discarded completions with an atomic counter exposed through `droppedCompletionCount()`.
- Keep render-thread ownership unchanged: callers still consume results through `poll()`.
- Extend the smoke test to force completion-queue saturation and verify the queue stays bounded and overflow is observable.

## Validation
GitHub Actions runs `stage59SmokeTest` alongside the existing Stage 11–58 checks. This is a CPU-side queue policy test; it does not replace an Android/MobileGlues device test.
