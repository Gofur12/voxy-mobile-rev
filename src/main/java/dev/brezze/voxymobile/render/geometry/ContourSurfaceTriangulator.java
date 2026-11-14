package dev.brezze.voxymobile.render.geometry;

import java.util.ArrayList;
import java.util.List;

/** Ear-clips a simple, planar, closed XYZ+RGBA contour into CPU-side triangle indices. */
public final class ContourSurfaceTriangulator {
    private ContourSurfaceTriangulator() {}
    public static final class Result {
        private final int[] indices; private final float[] normal;
        private Result(int[] i,float[] n){indices=i;normal=n;}
        public int triangleCount(){return indices.length/3;}
        public int[] copyIndices(){return indices.clone();}
        public float[] copyNormal(){return normal.clone();}
    }
    private record P(double x,double y) {}
    public static Result triangulate(float[] contour, boolean closed, float tolerance) {
        if(contour==null || contour.length%7!=0 || contour.length<28 || !closed || !Float.isFinite(tolerance) || tolerance<=0)
            throw new IllegalArgumentException("requires a closed contour with at least four packed points and positive tolerance");
        int n=contour.length/7;
        for(float v:contour) if(!Float.isFinite(v)) throw new IllegalArgumentException("non-finite vertex");
        if(dist2(contour,0,contour,(n-1)*7)<=tolerance*tolerance) n--;
        if(n<3) throw new IllegalArgumentException("contour has fewer than three unique points");
        double nx=0,ny=0,nz=0;
        for(int i=0;i<n;i++){int a=i*7,b=((i+1)%n)*7;double ax=contour[a],ay=contour[a+1],az=contour[a+2],bx=contour[b],by=contour[b+1],bz=contour[b+2]; nx+=(ay-by)*(az+bz);ny+=(az-bz)*(ax+bx);nz+=(ax-bx)*(ay+by);}
        double len=Math.sqrt(nx*nx+ny*ny+nz*nz); if(len<=tolerance*tolerance) throw new IllegalArgumentException("degenerate contour");
        nx/=len;ny/=len;nz/=len;
        int drop=Math.abs(nx)>=Math.abs(ny)&&Math.abs(nx)>=Math.abs(nz)?0:(Math.abs(ny)>=Math.abs(nz)?1:2);
        double[] plane={contour[0],contour[1],contour[2]};
        List<P> p=new ArrayList<>();
        for(int i=0;i<n;i++){int o=i*7;double x=contour[o],y=contour[o+1],z=contour[o+2];double d=(x-plane[0])*nx+(y-plane[1])*ny+(z-plane[2])*nz;if(Math.abs(d)>tolerance)throw new IllegalArgumentException("non-planar contour");p.add(drop==0?new P(y,z):drop==1?new P(x,z):new P(x,y));}
        double area=0;for(int i=0;i<n;i++){P a=p.get(i),b=p.get((i+1)%n);area+=a.x*b.y-b.x*a.y;} if(Math.abs(area)<=tolerance*tolerance)throw new IllegalArgumentException("zero projected area");
        double sign=Math.signum(area);List<Integer> remaining=new ArrayList<>();for(int i=0;i<n;i++)remaining.add(i);int[] out=new int[(n-2)*3];int k=0;int guard=0;
        while(remaining.size()>3 && guard++<n*n){boolean cut=false;for(int j=0;j<remaining.size();j++){int ia=remaining.get((j+remaining.size()-1)%remaining.size()),ib=remaining.get(j),ic=remaining.get((j+1)%remaining.size());P a=p.get(ia),b=p.get(ib),c=p.get(ic);if(sign*cross(a,b,c)<=tolerance*tolerance)continue;boolean contains=false;for(int id:remaining)if(id!=ia&&id!=ib&&id!=ic&&inside(p.get(id),a,b,c,sign,tolerance)){contains=true;break;}if(!contains){out[k++]=ia;out[k++]=ib;out[k++]=ic;remaining.remove(j);cut=true;break;}}if(!cut)throw new IllegalArgumentException("self-intersecting or non-simple contour");}
        if(remaining.size()!=3)throw new IllegalArgumentException("triangulation did not converge");for(int id:remaining)out[k++]=id;
        return new Result(out,new float[]{(float)nx,(float)ny,(float)nz});
    }
    private static double cross(P a,P b,P c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static boolean inside(P p,P a,P b,P c,double s,double e){return s*cross(a,b,p)>=-e&&s*cross(b,c,p)>=-e&&s*cross(c,a,p)>=-e;}
    private static double dist2(float[]a,int ao,float[]b,int bo){double x=a[ao]-b[bo],y=a[ao+1]-b[bo+1],z=a[ao+2]-b[bo+2];return x*x+y*y+z*z;}
}
