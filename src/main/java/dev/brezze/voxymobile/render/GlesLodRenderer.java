package dev.brezze.voxymobile.render;

import dev.brezze.voxymobile.core.ingest.ChunkSnapshotStore;
import dev.brezze.voxymobile.core.lod.LodNode;
import dev.brezze.voxymobile.core.lod.LodNodeTraversal;
import dev.brezze.voxymobile.core.lod.HierarchicalLodSelector;
import dev.brezze.voxymobile.core.lod.NodeDataSnapshot;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import dev.brezze.voxymobile.render.culling.FrustumCuller;
import dev.brezze.voxymobile.render.culling.SectionOcclusionCuller;
import dev.brezze.voxymobile.render.culling.SectionVisibilityPipeline;
import dev.brezze.voxymobile.render.streaming.AdaptiveLodStreamingController;
import dev.brezze.voxymobile.render.streaming.PredictiveStreamingController;
import dev.brezze.voxymobile.render.geometry.ParentGeometryCache;
import dev.brezze.voxymobile.render.geometry.ParentGeometryKey;
import dev.brezze.voxymobile.render.geometry.ParentGeometryUploadQueue;
import dev.brezze.voxymobile.render.geometry.ParentGpuMeshData;
import dev.brezze.voxymobile.render.geometry.ParentMeshBuildScheduler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Stage 07 GLES 3.2 path with asynchronous CPU meshing and bounded GPU uploads. Uses only GLES 3.0-era VAO/VBO/EBO and GLSL ES 3.00.
 * All GL allocation, upload and draw calls are made from the Minecraft render callback.
 */
public final class GlesLodRenderer {
    private static final int FLOATS_PER_VERTEX = 7; // xyz + rgba
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * Float.BYTES;
    private static final String VERTEX_SHADER = """
            #version 300 es
            precision highp float;
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec4 Color;
            uniform mat4 MVP;
            uniform vec3 CameraPosition;
            out vec4 vColor;
            void main() {
                gl_Position = MVP * vec4(Position - CameraPosition, 1.0);
                vColor = Color;
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec4 vColor;
            out vec4 FragColor;
            void main() { FragColor = vColor; }
            """;

    private final ChunkSnapshotStore store;
    private final MeshBuildScheduler scheduler = new MeshBuildScheduler();
    private final SectionOcclusionCuller occlusionCuller = new SectionOcclusionCuller();
    private final SectionVisibilityPipeline visibilityPipeline = new SectionVisibilityPipeline(occlusionCuller);
    private final AdaptiveLodStreamingController streaming = new AdaptiveLodStreamingController();
    private final PredictiveStreamingController predictiveStreaming = new PredictiveStreamingController();
    private final HierarchicalLodSelector hierarchySelector = new HierarchicalLodSelector();
    private final Map<SectionPos, GpuMesh> meshes = new HashMap<>();
    private final Map<ParentGeometryKey, ParentGpuMesh> parentMeshes = new HashMap<>();
    private final ParentGeometryCache parentGeometryCache = new ParentGeometryCache(24L * 1024L * 1024L);
    private final ParentGeometryUploadQueue parentUploadQueue = new ParentGeometryUploadQueue(128);
    private final ParentMeshBuildScheduler parentBuildScheduler = new ParentMeshBuildScheduler();
    private long gpuBytes;
    private final Map<SectionPos, Integer> selectedLods = new HashMap<>();
    private int program;
    private int mvpLocation = -1;
    private int cameraLocation = -1;
    private boolean failed;
    private long previousRenderNanos;
    private double cameraForwardX;
    private double cameraForwardY;
    private double cameraForwardZ = -1.0;

    public GlesLodRenderer(ChunkSnapshotStore store) { this.store = store; }

    public void render(WorldRenderContext context) {
        if (failed) return;
        try {
            if (program == 0) initializeProgram();
            Map<SectionPos, VoxelSection> current = new HashMap<>();
            for (VoxelSection section : store.snapshot()) current.put(section.position(), section);
            Set<SectionPos> live = new HashSet<>(current.keySet());
            for (SectionPos pos : new ArrayList<>(meshes.keySet())) {
                if (!live.contains(pos)) {
                    GpuMesh removed = meshes.remove(pos);
                    if (removed != null) { gpuBytes -= removed.byteSize; removed.delete(); }
                    selectedLods.remove(pos);
                    scheduler.invalidate(pos);
                }
            }
            for (SectionPos pos : new ArrayList<>(selectedLods.keySet())) {
                if (!live.contains(pos)) {
                    selectedLods.remove(pos);
                    scheduler.invalidate(pos);
                }
            }

            Vec3d cameraPos = context.camera().getPos();
            contextFreeCameraX = cameraPos.x; contextFreeCameraY = cameraPos.y; contextFreeCameraZ = cameraPos.z;
            Vector3f forward = context.camera().getRotation().transform(new Vector3f(0.0f, 0.0f, -1.0f));
            cameraForwardX = forward.x; cameraForwardY = forward.y; cameraForwardZ = forward.z;
            long frameNow = System.nanoTime();
            if (previousRenderNanos != 0L && frameNow > previousRenderNanos) {
                // Render callback interval includes frame pacing; EMA/hysteresis prevents one-off spikes.
                streaming.recordFrameTime((frameNow - previousRenderNanos) / 1_000_000.0);
            }
            previousRenderNanos = frameNow;
            streaming.update(cameraPos.x, cameraPos.y, cameraPos.z, frameNow);
            int buildRequests = 0;
            for (Map.Entry<SectionPos, VoxelSection> entry : current.entrySet()) {
                SectionPos pos = entry.getKey();
                GpuMesh old = meshes.get(pos);
                int previousLod = selectedLods.getOrDefault(pos, old == null ? -1 : old.lodLevel);
                int wantedLod = chooseLod(pos, cameraPos, previousLod);
                selectedLods.put(pos, wantedLod);
                if (old == null || old.source != entry.getValue() || old.lodLevel != wantedLod) {
                    boolean alreadyRequested = scheduler.hasRequest(pos, entry.getValue(), wantedLod);
                    if (alreadyRequested || buildRequests < streaming.maxNewBuildsPerFrame()) {
                        double cx = pos.x() * 16.0 + 8.0;
                        double cy = pos.y() * 16.0 + 8.0;
                        double cz = pos.z() * 16.0 + 8.0;
                        double predictedDistance = streaming.predictedDistanceSquared(cx, cy, cz, cameraPos.x, cameraPos.y, cameraPos.z);
                        double priority = predictiveStreaming.priorityDistanceSquared(cx, cy, cz,
                                cameraPos.x, cameraPos.y, cameraPos.z, cameraForwardX, cameraForwardY, cameraForwardZ,
                                streaming.velocityX(), streaming.velocityY(), streaming.velocityZ(), predictedDistance);
                        if (scheduler.request(entry.getValue(), wantedLod, priority) && !alreadyRequested) buildRequests++;
                    }
                }
            }
            occlusionCuller.retain(live);
            processCompletedUploads(current);
            processParentGeometry();
            draw(context, current);
        } catch (Throwable error) {
            // A driver-specific GLES failure should disable this optional renderer, not crash the client.
            failed = true;
            System.err.println("[VoxyMobile] GLES renderer disabled: " + error);
            error.printStackTrace();
            release();
        }
    }

    private void initializeProgram() {
        int vs = compile(GL20C.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vs);
        GL20C.glAttachShader(program, fs);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            program = 0;
            throw new IllegalStateException("GLSL link failed: " + log);
        }
        mvpLocation = GL20C.glGetUniformLocation(program, "MVP");
        cameraLocation = GL20C.glGetUniformLocation(program, "CameraPosition");
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("GLSL compile failed: " + log);
        }
        return shader;
    }

    private int chooseLod(SectionPos pos, Vec3d camera, int previousLod) {
        double dx = pos.x() * 16.0 + 8.0 - camera.x;
        double dy = pos.y() * 16.0 + 8.0 - camera.y;
        double dz = pos.z() * 16.0 + 8.0 - camera.z;
        return streaming.selectLod(dx * dx + dy * dy + dz * dz, previousLod);
    }

    /** Adaptive, bounded GPU uploads. All GL calls remain on Minecraft's render thread. */
    private void processCompletedUploads(Map<SectionPos, VoxelSection> current) {
        int budget = streaming.uploadsPerFrame(scheduler.completedCount());
        for (int uploaded = 0; uploaded < budget; uploaded++) {
            MeshBuildData data = scheduler.pollCompleted();
            if (data == null) break;
            VoxelSection latest = current.get(data.source().position());
            if (latest != data.source()) continue; // stale result; a fresh request is issued next frame
            if (data.isEmpty()) {
                GpuMesh removed = meshes.remove(data.source().position());
                if (removed != null) { gpuBytes -= removed.byteSize; removed.delete(); }
                LodNode leaf = store.lodTree().findLeaf(data.source().position());
                if (leaf != null) store.lodTree().markGeometryReady(data.source().position(), 0, leaf.contentRevision());
                continue;
            }
            GpuMesh replacement = upload(data);
            GpuMesh replaced = meshes.put(data.source().position(), replacement);
            gpuBytes += replacement.byteSize;
            if (replaced != null) { gpuBytes -= replaced.byteSize; replaced.delete(); }
            LodNode leaf = store.lodTree().findLeaf(data.source().position());
            if (leaf != null) store.lodTree().markGeometryReady(data.source().position(), 0, leaf.contentRevision());
            evictDistantMeshes(contextFreeCameraX, contextFreeCameraY, contextFreeCameraZ, data.source().position());
        }
    }

    /** Stage 17: schedule every populated parent level; CPU aggregation and meshing stay on the worker. */
    private void processParentGeometry() {
        List<LodNode> nodes = store.lodTree().nodesSnapshot();
        Set<ParentGeometryKey> live = new HashSet<>();
        int parentBuildRequests = 0;
        for (LodNode node : nodes) {
            if (node.level() <= 0) continue;
            ParentGeometryKey key = new ParentGeometryKey(node.origin(), node.level());
            live.add(key);
            if (node.hasCurrentGeometry()) continue;
            long revision = node.contentRevision();
            boolean alreadyRequested = parentBuildScheduler.hasRequest(key, revision);
            if (!alreadyRequested && parentBuildRequests >= streaming.maxNewBuildsPerFrame()) continue;
            ParentGpuMeshData cached = parentGeometryCache.get(key, revision);
            if (cached != null) {
                parentUploadQueue.offer(cached);
                continue;
            }
            NodeDataSnapshot snapshot = store.lodTree().snapshotNodeData(node.origin(), node.level());
            if (snapshot == null || snapshot.contentRevision() != revision) continue;
            double cx = node.origin().x() * 16.0 + node.spanSections() * 8.0;
            double cy = node.origin().y() * 16.0 + node.spanSections() * 8.0;
            double cz = node.origin().z() * 16.0 + node.spanSections() * 8.0;
            double dx = cx - contextFreeCameraX, dy = cy - contextFreeCameraY, dz = cz - contextFreeCameraZ;
            double predictedDistance = streaming.predictedDistanceSquared(cx, cy, cz,
                    contextFreeCameraX, contextFreeCameraY, contextFreeCameraZ);
            double priority = predictiveStreaming.priorityDistanceSquared(cx, cy, cz,
                    contextFreeCameraX, contextFreeCameraY, contextFreeCameraZ,
                    cameraForwardX, cameraForwardY, cameraForwardZ,
                    streaming.velocityX(), streaming.velocityY(), streaming.velocityZ(), predictedDistance);
            if (parentBuildScheduler.request(snapshot, priority) && !alreadyRequested) parentBuildRequests++;
        }

        for (ParentGeometryKey key : new ArrayList<>(parentMeshes.keySet())) {
            if (!live.contains(key)) {
                ParentGpuMesh removed = parentMeshes.remove(key);
                if (removed != null) { gpuBytes -= removed.byteSize; removed.delete(); }
                parentGeometryCache.invalidate(key);
                parentUploadQueue.remove(key);
                parentBuildScheduler.invalidate(key);
            }
        }

        // Worker results are revision checked before they enter the render-thread upload queue.
        int completedBudget = Math.max(1, streaming.uploadsPerFrame(parentBuildScheduler.completedCount()));
        for (int i = 0; i < completedBudget; i++) {
            ParentGpuMeshData data = parentBuildScheduler.pollCompleted();
            if (data == null) break;
            LodNode latest = store.lodTree().findNode(data.key().origin(), data.key().level());
            if (latest == null || latest.contentRevision() != data.contentRevision()) continue;
            parentGeometryCache.put(data);
            parentUploadQueue.offer(data);
        }

        int uploadBudget = streaming.uploadsPerFrame(parentUploadQueue.size());
        for (int i = 0; i < uploadBudget; i++) {
            ParentGpuMeshData data = parentUploadQueue.poll();
            if (data == null) break;
            LodNode node = store.lodTree().findNode(data.key().origin(), data.key().level());
            if (node == null || node.contentRevision() != data.contentRevision()) continue;
            if (data.isEmpty()) {
                ParentGpuMesh old = parentMeshes.remove(data.key());
                if (old != null) { gpuBytes -= old.byteSize; old.delete(); }
                continue;
            }
            ParentGpuMesh replacement = uploadParent(data);
            ParentGpuMesh old = parentMeshes.put(data.key(), replacement);
            gpuBytes += replacement.byteSize;
            if (old != null) { gpuBytes -= old.byteSize; old.delete(); }
            store.lodTree().markGeometryReady(data.key().origin(), data.key().level(), data.contentRevision());
            evictDistantMeshes(contextFreeCameraX, contextFreeCameraY, contextFreeCameraZ, data.key().origin());
        }
    }

    private ParentGpuMesh uploadParent(ParentGpuMeshData data) {
        float[] vertexArray = data.copyVertices();
        int[] indexArray = data.copyIndices();
        FloatBuffer vertices = BufferUtils.createFloatBuffer(vertexArray.length);
        vertices.put(vertexArray).flip();
        IntBuffer indices = BufferUtils.createIntBuffer(indexArray.length);
        indices.put(indexArray).flip();
        int vao = GL30C.glGenVertexArrays();
        int vbo = GL15C.glGenBuffers();
        int ebo = GL15C.glGenBuffers();
        GL30C.glBindVertexArray(vao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vertices, GL15C.GL_STATIC_DRAW);
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, indices, GL15C.GL_STATIC_DRAW);
        GL20C.glEnableVertexAttribArray(0);
        GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, BYTES_PER_VERTEX, 0L);
        GL20C.glEnableVertexAttribArray(1);
        GL20C.glVertexAttribPointer(1, 4, GL11C.GL_FLOAT, false, BYTES_PER_VERTEX, 3L * Float.BYTES);
        GL30C.glBindVertexArray(0);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        return new ParentGpuMesh(data.key(), data.contentRevision(), vao, vbo, ebo,
                indexArray.length, data.byteSize());
    }

    private double contextFreeCameraX, contextFreeCameraY, contextFreeCameraZ;

    /** Evicts the farthest leaf OR parent GPU allocation until the shared byte budget is met. */
    private void evictDistantMeshes(double cameraX, double cameraY, double cameraZ, SectionPos protectedPosition) {
        long budget = streaming.gpuBudgetBytes();
        while (gpuBytes > budget && meshes.size() + parentMeshes.size() > 1) {
            SectionPos farthestLeaf = null;
            ParentGeometryKey farthestParent = null;
            double farthestDistance = -1.0;
            for (SectionPos pos : meshes.keySet()) {
                if (pos.equals(protectedPosition)) continue;
                double dx = pos.x() * 16.0 + 8.0 - cameraX;
                double dy = pos.y() * 16.0 + 8.0 - cameraY;
                double dz = pos.z() * 16.0 + 8.0 - cameraZ;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance > farthestDistance) { farthestDistance = distance; farthestLeaf = pos; farthestParent = null; }
            }
            for (ParentGeometryKey key : parentMeshes.keySet()) {
                if (key.origin().equals(protectedPosition)) continue;
                double span = (1 << key.level()) * 16.0;
                double dx = key.origin().x() * 16.0 + span * 0.5 - cameraX;
                double dy = key.origin().y() * 16.0 + span * 0.5 - cameraY;
                double dz = key.origin().z() * 16.0 + span * 0.5 - cameraZ;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance > farthestDistance) { farthestDistance = distance; farthestParent = key; farthestLeaf = null; }
            }
            if (farthestLeaf != null) {
                GpuMesh removed = meshes.remove(farthestLeaf);
                if (removed != null) { gpuBytes -= removed.byteSize; removed.delete(); }
                store.lodTree().invalidateGeometry(farthestLeaf, 0);
            } else if (farthestParent != null) {
                ParentGpuMesh removed = parentMeshes.remove(farthestParent);
                if (removed != null) { gpuBytes -= removed.byteSize; removed.delete(); }
                parentGeometryCache.invalidate(farthestParent);
                parentUploadQueue.remove(farthestParent);
                parentBuildScheduler.invalidate(farthestParent);
                // A future traversal can request this parent again after its current flag is reset.
                store.lodTree().invalidateGeometry(farthestParent.origin(), farthestParent.level());
            } else break;
        }
    }

    private GpuMesh upload(MeshBuildData data) {
        float[] vertexArray = data.vertices();
        int[] indexArray = data.indices();
        FloatBuffer vertices = BufferUtils.createFloatBuffer(vertexArray.length);
        vertices.put(vertexArray).flip();
        IntBuffer indices = BufferUtils.createIntBuffer(indexArray.length);
        indices.put(indexArray).flip();
        int vao = GL30C.glGenVertexArrays();
        int vbo = GL15C.glGenBuffers();
        int ebo = GL15C.glGenBuffers();
        GL30C.glBindVertexArray(vao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vertices, GL15C.GL_STATIC_DRAW);
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, indices, GL15C.GL_STATIC_DRAW);
        GL20C.glEnableVertexAttribArray(0);
        GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, BYTES_PER_VERTEX, 0L);
        GL20C.glEnableVertexAttribArray(1);
        GL20C.glVertexAttribPointer(1, 4, GL11C.GL_FLOAT, false, BYTES_PER_VERTEX, 3L * Float.BYTES);
        GL30C.glBindVertexArray(0);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        long byteSize = (long) vertexArray.length * Float.BYTES + (long) indexArray.length * Integer.BYTES;
        return new GpuMesh(data.source(), data.lodLevel(), vao, vbo, ebo, indexArray.length, byteSize);
    }

    private void draw(WorldRenderContext context, Map<SectionPos, VoxelSection> current) {
        if (meshes.isEmpty() && parentMeshes.isEmpty()) return;
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        contextFreeCameraX = cameraPos.x; contextFreeCameraY = cameraPos.y; contextFreeCameraZ = cameraPos.z;
        MatrixStack matrices = context.matrixStack();
        Matrix4f mvp = new Matrix4f(context.projectionMatrix()).mul(matrices.peek().getPositionMatrix());
        FrustumCuller frustum = FrustumCuller.from(mvp);
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        mvp.get(matrix);
        matrix.flip();
        int previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int previousVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean previousDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);

        GL20C.glUseProgram(program);
        GL20C.glUniformMatrix4fv(mvpLocation, false, matrix);
        GL20C.glUniform3f(cameraLocation, (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthMask(true);
        GL11C.glDisable(GL11C.GL_CULL_FACE);
        GL11C.glDisable(GL11C.GL_BLEND);
        Set<SectionPos> visibleLeaves = new HashSet<>();
        for (SectionVisibilityPipeline.VisibleSection visible : visibilityPipeline.collect(
                meshes.keySet(), current, cameraPos.x, cameraPos.y, cameraPos.z,
                frustum, SectionVisibilityPipeline.DEFAULT_MAX_DISTANCE)) visibleLeaves.add(visible.position());
        for (LodNodeTraversal.Selection selection : LodNodeTraversal.collectByDistance(
                store.lodTree(), hierarchySelector, cameraPos.x, cameraPos.y, cameraPos.z)) {
            LodNode node = selection.node();
            SectionPos origin = node.origin();
            float minX = origin.x() * 16.0f, minY = origin.y() * 16.0f, minZ = origin.z() * 16.0f;
            float span = node.spanSections() * 16.0f;
            if (!frustum.isVisible(minX, minY, minZ, minX + span, minY + span, minZ + span)) continue;
            if (node.level() == 0) {
                if (!visibleLeaves.contains(origin)) continue;
                GpuMesh mesh = meshes.get(origin);
                if (mesh == null) continue;
                GL30C.glBindVertexArray(mesh.vao);
                GL11C.glDrawElements(GL11C.GL_TRIANGLES, mesh.indexCount, GL11C.GL_UNSIGNED_INT, 0L);
            } else {
                ParentGpuMesh mesh = parentMeshes.get(new ParentGeometryKey(origin, node.level()));
                if (mesh == null || mesh.revision != node.contentRevision()) continue;
                GL30C.glBindVertexArray(mesh.vao);
                GL11C.glDrawElements(GL11C.GL_TRIANGLES, mesh.indexCount, GL11C.GL_UNSIGNED_INT, 0L);
            }
        }
        GL30C.glBindVertexArray(previousVao);
        GL20C.glUseProgram(previousProgram);
        GL11C.glDepthMask(previousDepthMask);
        if (depthEnabled) GL11C.glEnable(GL11C.GL_DEPTH_TEST); else GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        if (cullEnabled) GL11C.glEnable(GL11C.GL_CULL_FACE); else GL11C.glDisable(GL11C.GL_CULL_FACE);
        if (blendEnabled) GL11C.glEnable(GL11C.GL_BLEND); else GL11C.glDisable(GL11C.GL_BLEND);
    }

    public void release() {
        for (GpuMesh mesh : meshes.values()) mesh.delete();
        meshes.clear();
        for (ParentGpuMesh mesh : parentMeshes.values()) mesh.delete();
        parentMeshes.clear();
        parentGeometryCache.clear();
        parentUploadQueue.clear();
        parentBuildScheduler.close();
        gpuBytes = 0L;
        selectedLods.clear();
        previousRenderNanos = 0L;
        hierarchySelector.clear();
        occlusionCuller.clear();
        visibilityPipeline.clear();
        scheduler.close();
        if (program != 0) { GL20C.glDeleteProgram(program); program = 0; }
    }

    private static final class GpuMesh {
        final VoxelSection source;
        final int lodLevel;
        final int vao, vbo, ebo, indexCount;
        final long byteSize;
        GpuMesh(VoxelSection source, int lodLevel, int vao, int vbo, int ebo, int indexCount, long byteSize) {
            this.source = source; this.lodLevel = lodLevel;
            this.vao = vao; this.vbo = vbo; this.ebo = ebo; this.indexCount = indexCount; this.byteSize = byteSize;
        }
        void delete() {
            GL30C.glDeleteVertexArrays(vao);
            GL15C.glDeleteBuffers(vbo);
            GL15C.glDeleteBuffers(ebo);
        }
    }
    private static final class ParentGpuMesh {
        final ParentGeometryKey key;
        final long revision;
        final int vao, vbo, ebo, indexCount;
        final long byteSize;
        ParentGpuMesh(ParentGeometryKey key, long revision, int vao, int vbo, int ebo,
                      int indexCount, long byteSize) {
            this.key = key; this.revision = revision; this.vao = vao; this.vbo = vbo;
            this.ebo = ebo; this.indexCount = indexCount; this.byteSize = byteSize;
        }
        void delete() {
            GL30C.glDeleteVertexArrays(vao);
            GL15C.glDeleteBuffers(vbo);
            GL15C.glDeleteBuffers(ebo);
        }
    }

}
