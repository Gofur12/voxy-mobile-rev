package dev.brezze.voxymobile.render;

import dev.brezze.voxymobile.core.lod.VoxelMipLevel;
import dev.brezze.voxymobile.core.mesh.GreedyLodMesher;
import dev.brezze.voxymobile.core.mesh.MeshQuad;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.List;

/** Immutable CPU-side mesh payload. Safe to create on a worker thread; contains no GL objects. */
public final class MeshBuildData {
    private static final int FLOATS_PER_VERTEX = 7;
    private final VoxelSection source;
    private final int lodLevel;
    private final float[] vertices;
    private final int[] indices;

    private MeshBuildData(VoxelSection source, int lodLevel, float[] vertices, int[] indices) {
        this.source = source;
        this.lodLevel = lodLevel;
        this.vertices = vertices;
        this.indices = indices;
    }

    public static MeshBuildData build(VoxelSection section, int lodLevel) {
        if (section == null) throw new IllegalArgumentException("section is null");
        if (lodLevel < 0 || lodLevel > 4) throw new IllegalArgumentException("lodLevel must be 0..4");
        VoxelMipLevel mip = VoxelMipLevel.fromSection(section);
        for (int i = 0; i < lodLevel; i++) mip = mip.downsample();
        List<MeshQuad> quads = GreedyLodMesher.mesh(mip);
        float[] vertices = new float[quads.size() * 4 * FLOATS_PER_VERTEX];
        int[] indices = new int[quads.size() * 6];
        float scale = 1 << lodLevel;
        float ox = section.position().x() * 16.0f;
        float oy = section.position().y() * 16.0f;
        float oz = section.position().z() * 16.0f;
        int vp = 0, ip = 0;
        for (MeshQuad q : quads) {
            float[] color = materialColor(q.material, q.face);
            float[][] corners = corners(q);
            int vertexBase = vp / FLOATS_PER_VERTEX;
            for (float[] point : corners) {
                vertices[vp++] = ox + point[0] * scale;
                vertices[vp++] = oy + point[1] * scale;
                vertices[vp++] = oz + point[2] * scale;
                vertices[vp++] = color[0];
                vertices[vp++] = color[1];
                vertices[vp++] = color[2];
                vertices[vp++] = 1.0f;
            }
            indices[ip++] = vertexBase;
            indices[ip++] = vertexBase + 1;
            indices[ip++] = vertexBase + 2;
            indices[ip++] = vertexBase;
            indices[ip++] = vertexBase + 2;
            indices[ip++] = vertexBase + 3;
        }
        return new MeshBuildData(section, lodLevel, vertices, indices);
    }

    private static float[][] corners(MeshQuad q) {
        int axis = q.face / 2, u = (axis + 1) % 3, v = (axis + 2) % 3;
        float[][] out = new float[4][3];
        int[] start = {q.x, q.y, q.z};
        int[] p0 = start.clone(), p1 = start.clone(), p2 = start.clone(), p3 = start.clone();
        p1[u] += q.width;
        p2[u] += q.width; p2[v] += q.height;
        p3[v] += q.height;
        for (int k = 0; k < 3; k++) {
            out[0][k] = p0[k]; out[1][k] = p1[k]; out[2][k] = p2[k]; out[3][k] = p3[k];
        }
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

    public VoxelSection source() { return source; }
    public int lodLevel() { return lodLevel; }
    float[] vertices() { return vertices; }
    int[] indices() { return indices; }
    public boolean isEmpty() { return indices.length == 0; }
}
