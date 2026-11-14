package dev.brezze.voxymobile.core.mesh;

import dev.brezze.voxymobile.core.lod.VoxelMipLevel;
import java.util.ArrayList;
import java.util.List;

/** CPU greedy mesher. Face IDs: -X=0,+X=1,-Y=2,+Y=3,-Z=4,+Z=5. */
public final class GreedyLodMesher {
    private GreedyLodMesher() {}

    public static List<MeshQuad> mesh(VoxelMipLevel level) {
        if (level == null) throw new IllegalArgumentException("level is null");
        final int n = level.edge();
        final List<MeshQuad> result = new ArrayList<>();
        for (int axis = 0; axis < 3; axis++) {
            final int u = (axis + 1) % 3;
            final int v = (axis + 2) % 3;
            final int[] p = new int[3];
            for (int slice = 0; slice <= n; slice++) {
                int[][] mask = new int[n][n];
                for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
                    p[axis] = slice - 1; p[u] = i; p[v] = j;
                    int a = inside(p, n) ? level.blockId(p[0], p[1], p[2]) : 0;
                    p[axis] = slice;
                    int b = inside(p, n) ? level.blockId(p[0], p[1], p[2]) : 0;
                    if (a != 0 && b == 0) mask[j][i] = a + 1;
                    else if (a == 0 && b != 0) mask[j][i] = -(b + 1);
                }
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n;) {
                        int value = mask[j][i];
                        if (value == 0) { i++; continue; }
                        int w = 1;
                        while (i + w < n && mask[j][i + w] == value) w++;
                        int h = 1;
                        outer: while (j + h < n) {
                            for (int k = 0; k < w; k++) if (mask[j + h][i + k] != value) break outer;
                            h++;
                        }
                        for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) mask[j + yy][i + xx] = 0;
                        int[] xyz = new int[3]; xyz[axis] = slice; xyz[u] = i; xyz[v] = j;
                        int positive = value > 0 ? 1 : 0;
                        result.add(new MeshQuad(xyz[0], xyz[1], xyz[2], w, h,
                                axis * 2 + positive, Math.abs(value) - 1));
                        i += w;
                    }
                }
            }
        }
        return result;
    }

    private static boolean inside(int[] p, int n) {
        return p[0] >= 0 && p[1] >= 0 && p[2] >= 0 && p[0] < n && p[1] < n && p[2] < n;
    }
}
