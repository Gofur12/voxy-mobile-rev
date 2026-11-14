package dev.brezze.voxymobile.render.culling;

import dev.brezze.voxymobile.core.voxel.SectionPos;

/** World-space section/node bounds converted to the camera-relative space consumed by the render frustum. */
public record CameraRelativeAabb(float minX, float minY, float minZ,
                                 float maxX, float maxY, float maxZ) {
    public static CameraRelativeAabb section(SectionPos position, double cameraX, double cameraY, double cameraZ) {
        if (position == null) throw new IllegalArgumentException("position cannot be null");
        return worldBounds(position.x() * 16.0, position.y() * 16.0, position.z() * 16.0,
                16.0, cameraX, cameraY, cameraZ);
    }

    public static CameraRelativeAabb node(SectionPos origin, int spanSections,
                                         double cameraX, double cameraY, double cameraZ) {
        if (origin == null) throw new IllegalArgumentException("origin cannot be null");
        if (spanSections <= 0) throw new IllegalArgumentException("spanSections must be positive");
        return worldBounds(origin.x() * 16.0, origin.y() * 16.0, origin.z() * 16.0,
                spanSections * 16.0, cameraX, cameraY, cameraZ);
    }

    private static CameraRelativeAabb worldBounds(double x, double y, double z, double span,
                                                   double cameraX, double cameraY, double cameraZ) {
        return new CameraRelativeAabb((float)(x-cameraX), (float)(y-cameraY), (float)(z-cameraZ),
                (float)(x+span-cameraX), (float)(y+span-cameraY), (float)(z+span-cameraZ));
    }

    public boolean isVisible(FrustumCuller frustum) {
        return frustum != null && frustum.isVisible(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
