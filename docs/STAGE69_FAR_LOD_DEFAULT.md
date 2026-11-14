# Stage 69 — Practical far-LOD default

## Change
- Set the independent LOD radius default to 4096 blocks (256 sections).
- Preserve the configurable 256–8192 block range.
- Extracted parsing and clamping into a pure policy class so malformed settings safely fall back and bounds can be tested without a Minecraft runtime.
- Existing user configuration is preserved; this default applies when the setting is absent or invalid.

## Device note
4096 is a starting default, not a performance guarantee. GPU upload limits, retained terrain availability, and Android frame pacing still constrain actual visible LOD. Users can lower the value in `config/voxymobile.properties` if needed.

## Validation
Stage 69 smoke test covers default, valid values, minimum/maximum clamping, and malformed input. CI does not prove visual behavior on Adreno 640.
