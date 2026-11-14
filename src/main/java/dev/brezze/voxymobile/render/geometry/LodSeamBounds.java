package dev.brezze.voxymobile.render.geometry;

/** Immutable world-space bounds for conservative seam-patch visibility tests. */
public record LodSeamBounds(float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ) {
    public LodSeamBounds {
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ)
            throw new IllegalArgumentException("invalid seam bounds");
    }

    public static LodSeamBounds fromPackedVertices(float[] vertices) {
        if (vertices == null || vertices.length == 0 || vertices.length % 7 != 0)
            throw new IllegalArgumentException("non-empty packed XYZ+RGBA vertices required");
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertices.length; i += 7) {
            float x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
                throw new IllegalArgumentException("non-finite seam position");
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return new LodSeamBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Returns minXYZ,maxXYZ translated into the camera-relative space used by the renderer. */
    public float[] cameraRelative(double cameraX, double cameraY, double cameraZ) {
        return new float[] {(float) (minX - cameraX), (float) (minY - cameraY), (float) (minZ - cameraZ),
                (float) (maxX - cameraX), (float) (maxY - cameraY), (float) (maxZ - cameraZ)};
    }
}
