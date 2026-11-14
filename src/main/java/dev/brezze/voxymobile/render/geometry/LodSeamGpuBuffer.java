package dev.brezze.voxymobile.render.geometry;

import dev.brezze.voxymobile.render.culling.FrustumCuller;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Render-thread-owned GLES 3.0 VAO/VBO/EBO allocation for one seam mesh. */
public final class LodSeamGpuBuffer {
    private final int vao, vbo, ebo, indexCount;
    private final long byteSize, revision;
    private final LodSeamBounds bounds;
    private final float[] debugVertices;

    private LodSeamGpuBuffer(int vao, int vbo, int ebo, int indexCount, long byteSize, long revision, LodSeamBounds bounds, float[] debugVertices) {
        this.vao = vao; this.vbo = vbo; this.ebo = ebo;
        this.indexCount = indexCount; this.byteSize = byteSize; this.revision = revision; this.bounds = bounds; this.debugVertices = debugVertices;
    }

    /** Must be called with the Minecraft GLES context current on its render thread. */
    public static LodSeamGpuBuffer upload(LodSeamGpuMeshData data) {
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("non-empty seam data required");
        float[] vertices = data.copyVertices();
        LodSeamBounds bounds = LodSeamBounds.fromPackedVertices(vertices);
        int[] indices = data.copyIndices();
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length).put(vertices);
        vertexBuffer.flip();
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length).put(indices);
        indexBuffer.flip();
        int vao = GL30C.glGenVertexArrays();
        int vbo = GL15C.glGenBuffers();
        int ebo = GL15C.glGenBuffers();
        try {
            GL30C.glBindVertexArray(vao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vertexBuffer, GL15C.GL_STATIC_DRAW);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15C.GL_STATIC_DRAW);
            int stride = LodSeamGpuMeshData.FLOATS_PER_VERTEX * Float.BYTES;
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, stride, 0L);
            GL20C.glEnableVertexAttribArray(1);
            GL20C.glVertexAttribPointer(1, 4, GL11C.GL_FLOAT, false, stride, 3L * Float.BYTES);
            GL30C.glBindVertexArray(0);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
            float[] debugVertices = LodSeamTriangleVisualizer.build(vertices, indices, 0, 0, 0);
            return new LodSeamGpuBuffer(vao, vbo, ebo, indices.length, data.byteSize(), data.contentRevision(), bounds, debugVertices);
        } catch (RuntimeException | Error error) {
            GL30C.glBindVertexArray(0);
            GL30C.glDeleteVertexArrays(vao);
            GL15C.glDeleteBuffers(vbo);
            GL15C.glDeleteBuffers(ebo);
            throw error;
        }
    }

    public void draw() {
        GL30C.glBindVertexArray(vao);
        GL11C.glDrawElements(GL11C.GL_TRIANGLES, indexCount, GL11C.GL_UNSIGNED_INT, 0L);
    }
    /** Draws only when the world-space seam AABB intersects the current camera frustum. */
    public boolean drawIfVisible(FrustumCuller frustum, double cameraX, double cameraY, double cameraZ) {
        if (frustum == null) return false;
        float[] relative = bounds.cameraRelative(cameraX, cameraY, cameraZ);
        if (!frustum.isVisible(relative[0], relative[1], relative[2], relative[3], relative[4], relative[5])) return false;
        draw();
        return true;
    }
    public LodSeamBounds bounds() { return bounds; }
    /** Returns a defensive copy of world-space triangle wireframe vertices for debug rendering. */
    public float[] copyDebugVertices() { return debugVertices.clone(); }
    public void delete() {
        GL30C.glDeleteVertexArrays(vao);
        GL15C.glDeleteBuffers(vbo);
        GL15C.glDeleteBuffers(ebo);
    }
    public int indexCount() { return indexCount; }
    public long byteSize() { return byteSize; }
    public long revision() { return revision; }
}
