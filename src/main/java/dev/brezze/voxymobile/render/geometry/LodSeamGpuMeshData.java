package dev.brezze.voxymobile.render.geometry;

/** Immutable CPU-side payload ready for render-thread VBO/EBO upload. No GL calls occur here. */
public final class LodSeamGpuMeshData {
    public static final int FLOATS_PER_VERTEX = 7;
    private final LodSeamKey key;
    private final long contentRevision;
    private final float[] vertices;
    private final int[] indices;

    public LodSeamGpuMeshData(LodSeamKey key, long contentRevision, LodSeamSurfaceMesh mesh) {
        this(key, contentRevision, mesh == null ? null : mesh.copyVertices(), mesh == null ? null : mesh.copyIndices());
    }

    public LodSeamGpuMeshData(LodSeamKey key, long contentRevision, LodSeamPatchData patch) {
        this(key, contentRevision, patch == null ? null : patch.copyVertices(), patch == null ? null : patch.copyIndices());
    }

    private LodSeamGpuMeshData(LodSeamKey key, long contentRevision, float[] vertices, int[] indices) {
        if (key == null || vertices == null || indices == null) throw new IllegalArgumentException("key and mesh payload are required");
        if (contentRevision < 0) throw new IllegalArgumentException("contentRevision must be non-negative");
        this.key = key;
        this.contentRevision = contentRevision;
        this.vertices = vertices;
        this.indices = indices;
        if (vertices.length % FLOATS_PER_VERTEX != 0 || indices.length % 3 != 0)
            throw new IllegalArgumentException("invalid indexed seam mesh layout");
        LodSeamGeometryValidator.Report report = LodSeamGeometryValidator.validate(vertices, indices);
        if (!report.valid()) throw new IllegalArgumentException("invalid seam geometry: " + report.status());
    }

    public LodSeamKey key() { return key; }
    public long contentRevision() { return contentRevision; }
    public int vertexCount() { return vertices.length / FLOATS_PER_VERTEX; }
    public int indexCount() { return indices.length; }
    public boolean isEmpty() { return indices.length == 0; }
    public long byteSize() { return (long) vertices.length * Float.BYTES + (long) indices.length * Integer.BYTES; }
    public float[] copyVertices() { return vertices.clone(); }
    public int[] copyIndices() { return indices.clone(); }
}
