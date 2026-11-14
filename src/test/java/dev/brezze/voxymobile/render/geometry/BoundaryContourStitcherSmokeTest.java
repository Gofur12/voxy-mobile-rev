package dev.brezze.voxymobile.render.geometry;
public final class BoundaryContourStitcherSmokeTest {
 private static int n; private static void check(boolean b,String m){n++;if(!b)throw new AssertionError(m);} private static void expect(Runnable r){n++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("expected IllegalArgumentException");}
 private static void seg(float[] a,int o,float x,float y){a[o]=x;a[o+1]=y;a[o+2]=0;a[o+3]=1;a[o+4]=0;a[o+5]=0;a[o+6]=1;}
 public static void main(String[]z){
  // shuffled two-edge chain + one duplicate segment
  float[] chain=new float[56];seg(chain,0,1,0);seg(chain,7,2,0);seg(chain,14,0,0);seg(chain,21,1,0);seg(chain,28,0,0);seg(chain,35,1,0);seg(chain,42,8,8);seg(chain,49,9,9);
  var c=BoundaryContourStitcher.stitch(chain,4,1e-4f,8);check(c.contourCount()==2,"two components");check(c.pointCount(0)==3,"chain ordered");check(!c.isClosed(0),"open chain");check(c.pointCount(1)==2,"isolated segment");
  float[] loop=new float[56];seg(loop,0,0,0);seg(loop,7,1,0);seg(loop,14,1,0);seg(loop,21,1,1);seg(loop,28,1,1);seg(loop,35,0,1);seg(loop,42,0,1);seg(loop,49,0,0);
  var l=BoundaryContourStitcher.stitch(loop,4,1e-4f,4);check(l.contourCount()==1,"one loop");check(l.isClosed(0),"closed loop");check(l.pointCount(0)==5,"loop returns to origin");
  check(l.copyContour(0).length==35,"packed point payload");float[] copy=l.copyContour(0);copy[0]=99;check(l.copyContour(0)[0]!=99,"defensive copy");
  var limited=BoundaryContourStitcher.stitch(chain,4,1e-4f,1);check(limited.contourCount()==1,"contour cap");
  expect(()->BoundaryContourStitcher.stitch(new float[1],1,1e-4f,2));expect(()->BoundaryContourStitcher.stitch(new float[14],1,0,2));
  float[] bad=new float[14];bad[0]=Float.NaN;expect(()->BoundaryContourStitcher.stitch(bad,1,1e-4f,2));
  System.out.println("BoundaryContourStitcherSmokeTest PASS (13 assertions)");
 }
}
