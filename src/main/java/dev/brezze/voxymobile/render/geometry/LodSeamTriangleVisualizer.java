package dev.brezze.voxymobile.render.geometry;

import java.util.Collection;

/** Builds camera-relative XYZ+RGBA line vertices for the actual indexed seam triangles. */
public final class LodSeamTriangleVisualizer {
    public static final int FLOATS_PER_VERTEX = 7;
    private static final int[][] EDGES = {{0,1},{1,2},{2,0}};
    private LodSeamTriangleVisualizer() {}

    /** Input arrays are packed XYZ+RGBA vertices and triangle indices; output is GL_LINES payload. */
    public static float[] build(float[] vertices, int[] indices, double cameraX, double cameraY, double cameraZ) {
        if (vertices == null || indices == null || vertices.length % FLOATS_PER_VERTEX != 0 || indices.length % 3 != 0)
            throw new IllegalArgumentException("invalid indexed seam mesh layout");
        int vertexCount = vertices.length / FLOATS_PER_VERTEX;
        for (int index : indices) if (index < 0 || index >= vertexCount)
            throw new IllegalArgumentException("seam index outside vertex array");
        float[] out = new float[indices.length * 2 * FLOATS_PER_VERTEX];
        int cursor = 0;
        for (int i = 0; i < indices.length; i += 3) {
            for (int[] edge : EDGES) {
                int a = indices[i + edge[0]] * FLOATS_PER_VERTEX;
                int b = indices[i + edge[1]] * FLOATS_PER_VERTEX;
                cursor = put(out, cursor, vertices, a, cameraX, cameraY, cameraZ);
                cursor = put(out, cursor, vertices, b, cameraX, cameraY, cameraZ);
            }
        }
        return out;
    }

    public static float[] combine(Collection<float[]> meshes) {
        if (meshes == null || meshes.isEmpty()) return new float[0];
        int length = 0;
        for (float[] mesh : meshes) if (mesh != null) length += mesh.length;
        float[] result = new float[length];
        int offset = 0;
        for (float[] mesh : meshes) if (mesh != null) { System.arraycopy(mesh, 0, result, offset, mesh.length); offset += mesh.length; }
        return result;
    }

    private static int put(float[] out, int cursor, float[] source, int offset,
                           double cameraX, double cameraY, double cameraZ) {
        out[cursor++] = (float) (source[offset] - cameraX);
        out[cursor++] = (float) (source[offset + 1] - cameraY);
        out[cursor++] = (float) (source[offset + 2] - cameraZ);
        out[cursor++] = 0.1f; out[cursor++] = 0.9f; out[cursor++] = 1.0f; out[cursor++] = 1.0f;
        return cursor;
    }
}
