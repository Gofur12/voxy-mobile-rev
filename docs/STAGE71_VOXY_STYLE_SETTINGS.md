# Stage 71 — Voxy-style in-game settings

Adds an optional Mod Menu configuration entry with General and Rendering pages. General exposes the independent LOD distance slider (256–8192 blocks) and terrain capture toggle. Rendering exposes renderer enable/disable and seam debug overlay. Settings persist in config/voxymobile.properties and distance changes apply live when the screen closes.

Mod Menu remains optional; without it the mod still loads normally. PC-only Sodium builder threads, environmental fog, and SSAO are not exposed because this GLES mobile renderer does not implement those controls.
