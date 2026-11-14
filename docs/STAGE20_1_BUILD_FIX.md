# Stage 20.1 Build Fix

## Fabric API import correction

`ClientPlayConnectionEvents` belongs to `net.fabricmc.fabric.api.client.networking.v1`,
not `net.fabricmc.fabric.api.client.event.lifecycle.v1`.

This patch changes only the import in `VoxyMobileClient.java` and increments the
project version to `0.1.10-stage20.1`. No streaming, LOD, visibility, or GLES
behavior is changed.

The GitHub Actions build must still be run to validate compilation against the
resolved Fabric API and Minecraft dependencies.
