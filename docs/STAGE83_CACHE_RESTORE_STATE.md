# Stage 83 — Cache restore terminal-state correctness

The cache worker now distinguishes three outcomes:

- **Successful completion:** the entire nearest-first cache scan finished without cancellation or I/O failure. `isLoadComplete()` is true.
- **Failure:** an I/O error terminated the scan. `isLoadFailed()` and `isLoadFinished()` are true, while `isLoadComplete()` remains false.
- **Cancellation:** closing/interruption stops the scan without reporting a successful or normally finished restore.

The client restore gate now waits for a terminal outcome and for both the loaded handoff queue and live pending-section queue to drain. A failed restore can therefore release the gate without falsely reporting that the cache was completely restored.

The Stage 83 smoke test also asserts the successful terminal-state contract after a bounded nearest-first restore. This stage does not claim Android runtime verification.
