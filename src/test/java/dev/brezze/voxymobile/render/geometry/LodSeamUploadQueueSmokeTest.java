package dev.brezze.voxymobile.render.geometry;

public final class LodSeamUploadQueueSmokeTest {
    private static int assertions;
    private static void ok(boolean v, String m) { assertions++; if (!v) throw new AssertionError(m); }
    private static void bad(Runnable r) { assertions++; try { r.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("expected invalid input"); }
    private static LodSeamSurfaceMesh mesh() {
        float[] c = new float[5 * 7];
        float[][] p = {{0,0,0},{2,0,0},{2,2,0},{0,2,0},{0,0,0}};
        for (int i=0;i<p.length;i++) { int o=i*7; c[o]=p[i][0]; c[o+1]=p[i][1]; c[o+2]=p[i][2]; c[o+3]=0.2f; c[o+4]=0.4f; c[o+5]=0.6f; c[o+6]=1f; }
        return LodSeamSurfaceMesh.build(c,true,1e-5f);
    }
    public static void main(String[] args) {
        LodSeamKey a = new LodSeamKey(9, 3), b = new LodSeamKey(3, 9);
        ok(a.equals(b), "key canonicalizes region order");
        bad(() -> new LodSeamKey(4,4));
        LodSeamGpuMeshData d1 = new LodSeamGpuMeshData(a, 1, mesh());
        ok(d1.vertexCount()==4 && d1.indexCount()==6, "surface copied into upload payload");
        ok(d1.byteSize()==136, "payload byte size");
        float[] v=d1.copyVertices(); v[0]=99; ok(d1.copyVertices()[0]==0f, "vertex defensive copy");
        int[] ix=d1.copyIndices(); ix[0]=99; ok(d1.copyIndices()[0]!=99, "index defensive copy");
        LodSeamUploadQueue q = new LodSeamUploadQueue(2);
        ok(q.offer(d1), "first offer accepted");
        ok(q.pendingRevision(a)==1, "pending revision is queryable");
        ok(!q.offer(new LodSeamGpuMeshData(a,0,mesh())), "stale revision rejected");
        ok(q.offer(new LodSeamGpuMeshData(b,2,mesh())), "new revision replaces pending payload");
        ok(q.pendingRevision(a)==2, "replacement revision is visible before upload");
        ok(q.size()==1 && q.poll().contentRevision()==2, "latest revision polled");
        ok(q.pendingRevision(a)==-1, "polled key is no longer pending");
        ok(q.poll()==null, "empty queue returns null");
        ok(q.offer(d1), "queue accepts after poll");
        ok(q.remove(a) && q.size()==0, "remove pending seam");
        ok(q.offer(d1), "offer before clear"); q.clear(); ok(q.size()==0, "clear queue");
        LodSeamUploadQueue bounded = new LodSeamUploadQueue(1);
        ok(bounded.offer(d1), "bounded queue accepts first");
        ok(!bounded.offer(new LodSeamGpuMeshData(new LodSeamKey(10,11),1,mesh())), "capacity rejects overflow");
        ok(bounded.rejectedCount()==1, "rejection count increments");
        bad(() -> new LodSeamGpuMeshData(a,-1,mesh()));
        System.out.println("LodSeamUploadQueueSmokeTest PASS (19 assertions)");
    }
}
