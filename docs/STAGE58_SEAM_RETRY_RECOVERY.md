# Stage 58 — Seam Retry Recovery

## Goal
Prevent deterministic seam-generation failures from being retried every render refresh.

## Behavior
- First retry waits 250 ms.
- Each consecutive failure for the same seam revision doubles the delay.
- Delay is capped at 8 seconds.
- A changed source revision is eligible immediately and starts a fresh retry history.
- Successful generation clears the retry history.
- Retry policy is removed with the seam lifecycle and on renderer release.
- Failure markers remain visible while a retry is waiting or running.

## Validation
Run `./gradlew stage58SmokeTest` or the full `./gradlew check`.
The smoke test uses caller-supplied monotonic timestamps and does not require Minecraft or GLES.
