# Stage 81 — Nearest-first persistent cache restore

Persistent terrain cache files are now restored in ascending distance from the player's position. Distance is measured from the player point to each 16×16×16 section AABB, so a section containing the player has zero distance and nearby sections are restored before distant ones.

The worker still performs file reads off the client/render thread, retains the existing bounded section count, and eventually restores the entire available cache rather than discarding far-field entries. Existing callers of the original loadAll(store, limit) API remain compatible and use the world origin as their default center.

This improves warm-up order for previously visited terrain; it does not discover or generate chunks that Minecraft has never delivered.
