package dev.brezze.voxymobile.render.geometry;

import java.util.Collection;

/** Builds camera-relative XYZ+RGBA line vertices for seam AABB diagnostics. */
public final class LodSeamDebugOverlay {
    public static final int FLOATS_PER_VERTEX = 7;
    private static final int[][] EDGES = {
            {0,1},{1,2},{2,3},{3,0}, {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
    };
    private LodSeamDebugOverlay() {}

    public static float[] build(Collection<LodSeamBounds> bounds, double cameraX, double cameraY, double cameraZ) {
        if (bounds == null || bounds.isEmpty()) return new float[0];
        int count = 0;
        for (LodSeamBounds b : bounds) if (b != null) count++;
        float[] out = new float[count * EDGES.length * 2 * FLOATS_PER_VERTEX];
        int cursor = 0;
        for (LodSeamBounds b : bounds) {
            if (b == null) continue;
            float x0=(float)(b.minX()-cameraX), y0=(float)(b.minY()-cameraY), z0=(float)(b.minZ()-cameraZ);
            float x1=(float)(b.maxX()-cameraX), y1=(float)(b.maxY()-cameraY), z1=(float)(b.maxZ()-cameraZ);
            float[][] p={{x0,y0,z0},{x1,y0,z0},{x1,y0,z1},{x0,y0,z1},
                         {x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}};
            for (int[] edge : EDGES) {
                cursor = vertex(out,cursor,p[edge[0]]);
                cursor = vertex(out,cursor,p[edge[1]]);
            }
        }
        return out;
    }

    private static int vertex(float[] out, int i, float[] p) {
        out[i++]=p[0]; out[i++]=p[1]; out[i++]=p[2];
        out[i++]=1.0f; out[i++]=0.35f; out[i++]=0.05f; out[i++]=1.0f;
        return i;
    }
}
