package dev.brezze.voxymobile.render.geometry;

public final class LodSeamSurfaceMeshSmokeTest {
    private static int assertions;
    private static void ok(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void bad(Runnable action) { assertions++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("expected invalid input"); }
    private static float[] square() {
        float[][] p = {{0,0,0},{2,0,0},{2,2,0},{0,2,0},{0,0,0}};
        float[] out = new float[p.length * 7];
        for (int i=0;i<p.length;i++) { int o=i*7; out[o]=p[i][0]; out[o+1]=p[i][1]; out[o+2]=p[i][2]; out[o+3]=0.25f; out[o+4]=0.5f; out[o+5]=0.75f; out[o+6]=1f; }
        return out;
    }
    public static void main(String[] args) {
        LodSeamSurfaceMesh mesh = LodSeamSurfaceMesh.build(square(), true, 1e-5f);
        ok(mesh.vertexCount()==4, "duplicate closing vertex removed");
        ok(mesh.indexCount()==6 && mesh.triangleCount()==2, "square triangulated");
        ok(mesh.byteSize()==4L*7*4+6L*4, "GPU payload byte size");
        ok(mesh.copyNormal()[2]>0.99f, "normal retained");
        float[] v=mesh.copyVertices(); ok(v[3]==0.25f && v[4]==0.5f && v[5]==0.75f && v[6]==1f, "RGBA retained");
        int[] ix=mesh.copyIndices(); ix[0]=99; ok(mesh.copyIndices()[0]!=99, "indices defensive copy");
        v[0]=99; ok(mesh.copyVertices()[0]==0f, "vertices defensive copy");
        float[] normal=mesh.copyNormal(); normal[2]=0; ok(mesh.copyNormal()[2]>0.99f, "normal defensive copy");
        bad(() -> LodSeamSurfaceMesh.build(square(), false, 1e-5f));
        bad(() -> LodSeamSurfaceMesh.build(square(), true, 0f));
        System.out.println("LodSeamSurfaceMeshSmokeTest PASS (10 assertions)");
    }
}
