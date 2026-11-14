# Stage 72 — Voxy PC-style grouped settings

The Mod Menu screen now follows the Voxy PC configuration structure with two pages:
- General: master enable and terrain ingestion groups.
- Rendering: renderer enable, independent LOD distance, and seam debug group.

The master switch pauses rendering and terrain ingestion while preserving each sub-setting, so turning the master switch back on restores the previous choices. The page exposes only settings currently connected to the GLES mobile runtime; PC-only Sodium builder, fog, and SSAO controls are intentionally not presented as functional options.

Validation: Stage 72 policy smoke test covers master gating and independent sub-feature toggles. UI still requires an in-game Android/Mod Menu visual check.
