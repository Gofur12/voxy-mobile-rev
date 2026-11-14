# Stage 77 — Atomic LOD transition

- Freezes render-resource availability per traversal using an identity-keyed frame-local snapshot.
- This prevents an asynchronous GPU mesh becoming ready or being retired midway through hierarchy selection from making sibling decisions against different readiness states.
- Parent fallback remains selected until every populated child has a renderable current mesh; the child set replaces the parent as one selection result. If a child becomes unavailable, the parent fallback is selected again.
- Adds smoke coverage for 7/8 child readiness, 8/8 handoff, and readiness regression.
- This validates CPU selection atomicity; it does not imply GPU fences or device-side Android verification.
