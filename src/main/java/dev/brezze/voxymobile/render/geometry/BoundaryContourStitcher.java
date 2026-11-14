package dev.brezze.voxymobile.render.geometry;

import java.util.*;

/** Joins unordered XYZ+RGBA boundary segments into deterministic polylines. CPU-only. */
public final class BoundaryContourStitcher {
    private BoundaryContourStitcher() {}
    public static final class Contours {
        private final List<float[]> lines;
        private final boolean[] closed;
        private Contours(List<float[]> lines, boolean[] closed) { this.lines = lines; this.closed = closed; }
        public int contourCount() { return lines.size(); }
        public float[] copyContour(int i) { return lines.get(i).clone(); }
        public boolean isClosed(int i) { return closed[i]; }
        public int pointCount(int i) { return lines.get(i).length / 7; }
    }
    private record Key(long x,long y,long z) {}
    private static final class Node { final float[] p; final List<Integer> edges = new ArrayList<>(); Node(float[] p){this.p=p;} }
    private record Edge(int a,int b) {}

    /**
     * Deduplicates coincident endpoints and stitches segments. Open chains are emitted
     * before closed loops; branches are split into deterministic trails rather than guessed.
     */
    public static Contours stitch(float[] packedSegments, int segmentCount, float tolerance, int maxContours) {
        if (packedSegments == null || packedSegments.length != (long)segmentCount * 14)
            throw new IllegalArgumentException("expected two packed vertices per segment");
        if (segmentCount < 0 || !Float.isFinite(tolerance) || tolerance <= 0 || maxContours < 1)
            throw new IllegalArgumentException("invalid segment count, tolerance, or contour limit");
        Map<Key,Integer> ids = new LinkedHashMap<>(); List<Node> nodes = new ArrayList<>(); List<Edge> edges = new ArrayList<>();
        for (int s=0;s<segmentCount;s++) {
            float[] a = vertex(packedSegments,s*14), b = vertex(packedSegments,s*14+7);
            int ai=node(a,tolerance,ids,nodes), bi=node(b,tolerance,ids,nodes);
            if (ai==bi) continue;
            boolean duplicate=false; for(int e:nodes.get(ai).edges){ Edge old=edges.get(e); if((old.a==ai&&old.b==bi)||(old.a==bi&&old.b==ai)){duplicate=true;break;} }
            if(duplicate) continue;
            int ei=edges.size(); edges.add(new Edge(ai,bi)); nodes.get(ai).edges.add(ei); nodes.get(bi).edges.add(ei);
        }
        boolean[] used=new boolean[edges.size()]; List<float[]> lines=new ArrayList<>(); List<Boolean> closed=new ArrayList<>();
        // Start at endpoints/branches first, yielding stable open trails.
        for(int n=0;n<nodes.size()&&lines.size()<maxContours;n++) if(nodes.get(n).edges.size()!=2)
            for(int e:nodes.get(n).edges) if(!used[e]&&lines.size()<maxContours) walk(n,e,nodes,edges,used,lines,closed);
        // Remaining components are cycles.
        for(int e=0;e<edges.size()&&lines.size()<maxContours;e++) if(!used[e]) walk(edges.get(e).a,e,nodes,edges,used,lines,closed);
        boolean[] flags=new boolean[closed.size()]; for(int i=0;i<flags.length;i++) flags[i]=closed.get(i);
        return new Contours(List.copyOf(lines),flags);
    }
    private static void walk(int start,int first,List<Node> nodes,List<Edge> edges,boolean[] used,List<float[]> out,List<Boolean> closed){
        List<Float> pts=new ArrayList<>(); int current=start, edge=first; append(pts,nodes.get(current).p); boolean loop=false;
        while(edge>=0&&!used[edge]){ used[edge]=true; Edge e=edges.get(edge); int next=e.a==current?e.b:e.a; append(pts,nodes.get(next).p); if(next==start){loop=true;break;} int candidate=-1; for(int x:nodes.get(next).edges) if(!used[x]){candidate=x;break;} current=next; edge=candidate; }
        float[] arr=new float[pts.size()]; for(int i=0;i<arr.length;i++)arr[i]=pts.get(i); out.add(arr); closed.add(loop);
    }
    private static int node(float[] p,float eps,Map<Key,Integer> ids,List<Node> nodes){ Key k=new Key(Math.round(p[0]/eps),Math.round(p[1]/eps),Math.round(p[2]/eps)); Integer id=ids.get(k); if(id!=null&&dist2(nodes.get(id).p,p)<=eps*eps)return id; int n=nodes.size(); nodes.add(new Node(p)); ids.put(k,n); return n; }
    private static float[] vertex(float[] data,int o){float[] p=new float[7]; for(int i=0;i<7;i++){p[i]=data[o+i];if(!Float.isFinite(p[i]))throw new IllegalArgumentException("non-finite vertex");}return p;}
    private static float dist2(float[]a,float[]b){float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return x*x+y*y+z*z;}
    private static void append(List<Float> out,float[] p){for(float v:p)out.add(v);}
}
