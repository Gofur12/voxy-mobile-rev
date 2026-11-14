# Stage 65 — Persistent terrain cache core

- Adds a versioned compressed per-section disk format (.vms) preserving palette/index data.
- Separates cache directories by sanitized world and dimension namespace.
- Uses temporary files and atomic replacement where supported.
- Ignores truncated, corrupt, and unsupported-version entries.
- Bounds cache by file count and total bytes; defaults: 8,192 sections and 256 MiB.
- Provides bounded restore and explicit invalidation.

**Scope note:** this stage adds and tests the persistence engine. Runtime world identity/lifecycle wiring and asynchronous restore/save scheduling are the next integration step; this commit alone does not prove in-game restart persistence. Cache entries only contain terrain previously observed by Minecraft and cannot discover never-loaded chunks.
