# Stage 82 — Bounded cache restore handoff

Persistent cache restore now streams decoded sections from the disk worker into a fixed 128-entry handoff queue. When the client tick cannot consume quickly enough, the worker blocks on the bounded queue instead of retaining every decoded VoxelSection in a second in-memory collection. The client continues to poll at its existing per-tick budget, and all valid cache entries are eventually delivered in the nearest-first order established in Stage 81.

Closing the worker interrupts a blocked producer so a world switch does not leave a cache restore thread waiting indefinitely. Corrupt entries remain skippable. This bounds the temporary restore handoff; the primary terrain store still has its existing section-count LRU limit.
