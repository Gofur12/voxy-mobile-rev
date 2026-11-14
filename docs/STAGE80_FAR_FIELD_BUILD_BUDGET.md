# Stage 80 — Independent far-field parent build budget

- Parent LOD generation now has its own per-frame request allowance, separate from detailed leaf mesh requests.
- Stationary/default quality permits up to two parent builds per frame; fast camera movement reduces this to one.
- Under degraded adaptive quality tiers, parent build allowance is also reduced to one to protect frame time.
- Coarse-first scheduler priority is preserved, so parent coverage is generated before fine refinement at comparable distance.
- Added smoke assertions for stationary and fast-motion budgets.
- This improves scheduling fairness; actual far terrain still requires previously ingested/cached world data.
