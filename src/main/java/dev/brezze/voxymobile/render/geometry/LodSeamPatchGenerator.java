package dev.brezze.voxymobile.render.geometry;

/**
 * CPU-only end-to-end seam patch builder. Extracts matching boundary polylines
 * from two packed terrain meshes and feeds them into the zipper payload builder.
 * Coordinates must already share the renderer's world-space coordinate system.
 */
public final class LodSeamPatchGenerator {
    private LodSeamPatchGenerator() {}

    /** Describes one axis-aligned edge query against a packed XYZ+RGBA mesh. */
    public static final class EdgeQuery {
        public final int normalAxis;
        public final float plane;
        public final int tangentAxis;
        public final int fixedAxis;
        public final float fixedValue;
        public final float tolerance;

        public EdgeQuery(int normalAxis, float plane, int tangentAxis,
                         int fixedAxis, float fixedValue, float tolerance) {
            this.normalAxis = normalAxis;
            this.plane = plane;
            this.tangentAxis = tangentAxis;
            this.fixedAxis = fixedAxis;
            this.fixedValue = fixedValue;
            this.tolerance = tolerance;
        }

        float[] extract(float[] mesh) {
            return MeshBoundaryExtractor.extractEdge(mesh, normalAxis, plane,
                    tangentAxis, fixedAxis, fixedValue, tolerance);
        }
    }

    /** Extracts both edges and builds the packed XYZ+RGBA zipper patch. */
    public static LodSeamPatchData generate(
            float[] fineMesh, EdgeQuery fineQuery,
            float[] coarseMesh, EdgeQuery coarseQuery,
            float red, float green, float blue, float alpha) {
        if (fineMesh == null || coarseMesh == null || fineQuery == null || coarseQuery == null)
            throw new IllegalArgumentException("meshes and edge queries must not be null");
        float[] fineEdge = fineQuery.extract(fineMesh);
        float[] coarseEdge = coarseQuery.extract(coarseMesh);
        if (fineEdge.length < 6)
            throw new IllegalArgumentException("fine mesh boundary did not contain at least two points");
        if (coarseEdge.length < 6)
            throw new IllegalArgumentException("coarse mesh boundary did not contain at least two points");
        return LodSeamPatchData.build(fineEdge, coarseEdge, red, green, blue, alpha);
    }
}
