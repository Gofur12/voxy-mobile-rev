package dev.brezze.voxymobile.core.mesh;

/** Axis-aligned greedy quad in local mip-grid coordinates. */
public final class MeshQuad {
    public final int x, y, z, width, height, face, material;
    public MeshQuad(int x, int y, int z, int width, int height, int face, int material) {
        this.x=x; this.y=y; this.z=z; this.width=width; this.height=height;
        this.face=face; this.material=material;
    }
}
