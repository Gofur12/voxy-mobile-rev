package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.core.lod.ParentLodMeshData;
import dev.brezze.voxymobile.core.mesh.MeshQuad;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import java.util.List;

/** Immutable GLES-ready CPU arrays. Creating this object performs no OpenGL calls. */
public final class ParentGpuMeshData {
    public static final int FLOATS_PER_VERTEX = 7; // xyz + rgba
    private final ParentGeometryKey key;
    private final long contentRevision;
    private final float[] vertices;
    private final int[] indices;

    private ParentGpuMeshData(ParentGeometryKey key, long contentRevision, float[] vertices, int[] indices) {
        this.key = key;
        this.contentRevision = contentRevision;
        this.vertices = vertices;
        this.indices = indices;
    }

    public static ParentGpuMeshData from(ParentLodMeshData mesh, long contentRevision) {
        if (mesh == null) throw new IllegalArgumentException("mesh is null");
        if (mesh.level() <= 0) throw new IllegalArgumentException("expected parent LOD mesh");
        List<MeshQuad> quads = mesh.quads();
        float[] vertices = new float[quads.size() * 4 * FLOATS_PER_VERTEX];
        int[] indices = new int[quads.size() * 6];
        SectionPos origin = mesh.origin();
        float ox = origin.x() * 16.0f;
        float oy = origin.y() * 16.0f;
        float oz = origin.z() * 16.0f;
        float scale = mesh.cellBlockSize();
        int vp = 0, ip = 0;
        for (MeshQuad quad : quads) {
            float[] color = materialColor(quad.material, quad.face);
            float[][] corners = corners(quad);
            int base = vp / FLOATS_PER_VERTEX;
            for (float[] point : corners) {
                vertices[vp++] = ox + point[0] * scale;
                vertices[vp++] = oy + point[1] * scale;
                vertices[vp++] = oz + point[2] * scale;
                vertices[vp++] = color[0];
                vertices[vp++] = color[1];
                vertices[vp++] = color[2];
                vertices[vp++] = 1.0f;
            }
            indices[ip++] = base;
            indices[ip++] = base + 1;
            indices[ip++] = base + 2;
            indices[ip++] = base;
            indices[ip++] = base + 2;
            indices[ip++] = base + 3;
        }
        return new ParentGpuMeshData(new ParentGeometryKey(origin, mesh.level()), contentRevision, vertices, indices);
    }

    private static float[][] corners(MeshQuad q) {
        int axis = q.face / 2, u = (axis + 1) % 3, v = (axis + 2) % 3;
        int[] start = {q.x, q.y, q.z};
        int[][] points = {start.clone(), start.clone(), start.clone(), start.clone()};
        points[1][u] += q.width;
        points[2][u] += q.width; points[2][v] += q.height;
        points[3][v] += q.height;
        float[][] out = new float[4][3];
        for (int i = 0; i < 4; i++) for (int axisIndex = 0; axisIndex < 3; axisIndex++) out[i][axisIndex] = points[i][axisIndex];
        return out;
    }

    private static float[] materialColor(int id, int face) {
        int h = id * 0x45d9f3b;
        h ^= h >>> 16;
        float r = 0.35f + ((h >>> 16) & 255) / 510.0f;
        float g = 0.35f + ((h >>> 8) & 255) / 510.0f;
        float b = 0.35f + (h & 255) / 510.0f;
        float shade = switch (face) { case 2 -> 0.62f; case 3 -> 1.0f; case 0, 1 -> 0.82f; default -> 0.72f; };
        return new float[]{r * shade, g * shade, b * shade};
    }

    public ParentGeometryKey key() { return key; }
    public long contentRevision() { return contentRevision; }
    public int vertexCount() { return vertices.length / FLOATS_PER_VERTEX; }
    public int indexCount() { return indices.length; }
    public boolean isEmpty() { return indices.length == 0; }
    public long byteSize() { return (long) vertices.length * Float.BYTES + (long) indices.length * Integer.BYTES; }
    public float[] copyVertices() { return vertices.clone(); }
    public int[] copyIndices() { return indices.clone(); }
}
