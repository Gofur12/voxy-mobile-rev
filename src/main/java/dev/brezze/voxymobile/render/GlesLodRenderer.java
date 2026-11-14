package dev.brezze.voxymobile.render;

import dev.brezze.voxymobile.core.ingest.ChunkSnapshotStore;
import dev.brezze.voxymobile.diagnostics.RuntimeDiagnostics;
import dev.brezze.voxymobile.core.lod.LodNode;
import dev.brezze.voxymobile.core.lod.LodNodeTraversal;
import dev.brezze.voxymobile.core.lod.HierarchicalLodSelector;
import dev.brezze.voxymobile.core.lod.NodeDataSnapshot;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import dev.brezze.voxymobile.render.culling.FrustumCuller;
import dev.brezze.voxymobile.render.culling.CameraRelativeAabb;
import dev.brezze.voxymobile.render.culling.SectionOcclusionCuller;
import dev.brezze.voxymobile.render.culling.SectionVisibilityPipeline;
import dev.brezze.voxymobile.render.streaming.AdaptiveLodStreamingController;
import dev.brezze.voxymobile.render.streaming.PredictiveStreamingController;
import dev.brezze.voxymobile.render.streaming.VisibilityAwareStreamingController;
import dev.brezze.voxymobile.render.geometry.ParentGeometryCache;
import dev.brezze.voxymobile.render.geometry.GpuMemoryBudgetController;
import dev.brezze.voxymobile.render.geometry.GpuUploadBudgetController;
import dev.brezze.voxymobile.render.geometry.GpuMeshEvictionPolicy;
import dev.brezze.voxymobile.render.geometry.ParentGeometryKey;
import dev.brezze.voxymobile.render.geometry.ParentGeometryUploadQueue;
import dev.brezze.voxymobile.render.geometry.ParentGpuMeshData;
import dev.brezze.voxymobile.render.geometry.ParentMeshBuildScheduler;
import dev.brezze.voxymobile.render.geometry.LodSeamGpuMeshData;
import dev.brezze.voxymobile.render.geometry.LodSeamBounds;
import dev.brezze.voxymobile.render.geometry.LodSeamDebugOverlay;
import dev.brezze.voxymobile.render.geometry.LodSeamTriangleVisualizer;
import dev.brezze.voxymobile.render.geometry.LodSeamLiveProducer;
import dev.brezze.voxymobile.render.geometry.LodNeighborDetector;
import dev.brezze.voxymobile.render.geometry.LodSeamGpuBuffer;
import dev.brezze.voxymobile.render.geometry.LodSeamKey;
import dev.brezze.voxymobile.render.geometry.LodSeamUploadQueue;
import dev.brezze.voxymobile.render.geometry.LodSeamRevisionTracker;
import dev.brezze.voxymobile.render.geometry.LodSeamAsyncBuildQueue;
import dev.brezze.voxymobile.render.geometry.LodSeamSmartScheduler;
import dev.brezze.voxymobile.render.geometry.LodSeamRuntimeMetrics;
import dev.brezze.voxymobile.render.geometry.LodSeamFailureMarker;
import dev.brezze.voxymobile.render.geometry.LodSeamFailureOverlay;
import dev.brezze.voxymobile.render.geometry.LodSeamRetryPolicy;
import dev.brezze.voxymobile.render.geometry.LodSelectionNeighborBridge;
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
            // Mesh vertices are stored in absolute world coordinates. The vanilla world
            // render matrix supplies view rotation; translate world coordinates here once.
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
    private final VisibilityAwareStreamingController visibilityStreaming = new VisibilityAwareStreamingController();
    private final HierarchicalLodSelector hierarchySelector = new HierarchicalLodSelector();
    private final Map<SectionPos, GpuMesh> meshes = new HashMap<>();
    private final Map<ParentGeometryKey, ParentGpuMesh> parentMeshes = new HashMap<>();
    private final Map<LodSeamKey, LodSeamGpuBuffer> seamMeshes = new HashMap<>();
    private final Map<LodSeamKey, LodSeamBounds> seamCandidateBounds = new HashMap<>();
    private final Map<LodSeamKey, LodSeamFailureMarker> seamFailureMarkers = new HashMap<>();
    private final Set<LodSeamKey> activeSeamKeys = new HashSet<>();
    private final LodSeamUploadQueue seamUploadQueue = new LodSeamUploadQueue(64);
    private final LodSeamRevisionTracker seamRevisionTracker = new LodSeamRevisionTracker();
    private final LodSeamAsyncBuildQueue seamBuildQueue = new LodSeamAsyncBuildQueue(1, 16);
    private final LodSeamRuntimeMetrics seamRuntimeMetrics = new LodSeamRuntimeMetrics();
    private final LodSeamRetryPolicy seamRetryPolicy = new LodSeamRetryPolicy();
    private static final long MAX_SEAM_UPLOAD_BYTES_PER_FRAME = 512L * 1024L;
    private final ParentGeometryCache parentGeometryCache = new ParentGeometryCache(24L * 1024L * 1024L);
    private final GpuMemoryBudgetController gpuMemoryBudget = new GpuMemoryBudgetController();
    private final GpuUploadBudgetController gpuUploadBudget = new GpuUploadBudgetController();
    private MeshBuildData deferredLeafUpload;
    private final ParentGeometryUploadQueue parentUploadQueue = new ParentGeometryUploadQueue(128);
    private final ParentMeshBuildScheduler parentBuildScheduler;
    private long gpuBytes;
    private final Map<SectionPos, Integer> selectedLods = new HashMap<>();
    private int program;
    private int seamDebugVao;
    private int seamDebugVbo;
    private boolean seamDebugOverlayEnabled;
    private int mvpLocation = -1;
    private int cameraLocation = -1;
    private boolean failed;
    private boolean enabled = true;
    private long previousRenderNanos;
    private double cameraForwardX;
    private double cameraForwardY;
    private double cameraForwardZ = -1.0;
    private int neighborRefreshTicks;
    private volatile double maxLodDistance = 2048.0;

    public GlesLodRenderer(ChunkSnapshotStore store) { this(store, 1); }
    public GlesLodRenderer(ChunkSnapshotStore store, int serviceThreads) {
        this.store = store; this.parentBuildScheduler = new ParentMeshBuildScheduler(serviceThreads);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void render(WorldRenderContext context) {
        if (failed || !enabled) return;
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
            Matrix4f schedulingMvp = new Matrix4f(context.projectionMatrix())
                    .mul(context.matrixStack().peek().getPositionMatrix());
            FrustumCuller schedulingFrustum = FrustumCuller.from(schedulingMvp);
            int buildRequests = 0;
            double maxLodDistanceSquared = maxLodDistance * maxLodDistance;
            for (Map.Entry<SectionPos, VoxelSection> entry : current.entrySet()) {
                SectionPos pos = entry.getKey();
                double leafDx = pos.x() * 16.0 + 8.0 - cameraPos.x;
                double leafDy = pos.y() * 16.0 + 8.0 - cameraPos.y;
                double leafDz = pos.z() * 16.0 + 8.0 - cameraPos.z;
                if (leafDx * leafDx + leafDy * leafDy + leafDz * leafDz > maxLodDistanceSquared) continue;
                GpuMesh old = meshes.get(pos);
                int previousLod = selectedLods.getOrDefault(pos, old == null ? -1 : old.lodLevel);
                int wantedLod = chooseLod(pos, cameraPos, previousLod);
                selectedLods.put(pos, wantedLod);
                if (old == null || old.source != entry.getValue() || old.lodLevel != wantedLod) {
                    boolean deferredMatches = deferredLeafUpload != null
                            && deferredLeafUpload.source() == entry.getValue()
                            && deferredLeafUpload.lodLevel() == wantedLod;
                    boolean alreadyRequested = deferredMatches || scheduler.hasRequest(pos, entry.getValue(), wantedLod);
                    if (!deferredMatches && (alreadyRequested || buildRequests < streaming.maxNewBuildsPerFrame())) {
                        double cx = pos.x() * 16.0 + 8.0;
                        double cy = pos.y() * 16.0 + 8.0;
                        double cz = pos.z() * 16.0 + 8.0;
                        double predictedDistance = streaming.predictedDistanceSquared(cx, cy, cz, cameraPos.x, cameraPos.y, cameraPos.z);
                        double priority = predictiveStreaming.priorityDistanceSquared(cx, cy, cz,
                                cameraPos.x, cameraPos.y, cameraPos.z, cameraForwardX, cameraForwardY, cameraForwardZ,
                                streaming.velocityX(), streaming.velocityY(), streaming.velocityZ(), predictedDistance);
                        double minX = pos.x() * 16.0 - cameraPos.x;
                        double minY = pos.y() * 16.0 - cameraPos.y;
                        double minZ = pos.z() * 16.0 - cameraPos.z;
                        boolean inFrustum = schedulingFrustum.isVisible((float) minX, (float) minY, (float) minZ,
                                (float) (minX + 16.0), (float) (minY + 16.0), (float) (minZ + 16.0));
                        boolean enclosed = occlusionCuller.isOccluded(entry.getValue(), current);
                        priority = visibilityStreaming.sectionPriority(priority, inFrustum, enclosed);
                        if (scheduler.request(entry.getValue(), wantedLod, priority) && !alreadyRequested) buildRequests++;
                    }
                }
            }
            occlusionCuller.retain(live);
            gpuUploadBudget.beginFrame(gpuBytes, gpuMemoryBudget.pressureLevel(gpuBytes));
            processCompletedUploads(current);
            processParentGeometry(schedulingFrustum);
            processCompletedSeamBuilds();
            processSeamUploads();
            seamRuntimeMetrics.gauges(seamBuildQueue.pendingCount(), seamBuildQueue.completedCount(),
                    seamUploadQueue.size(), seamMeshes.size(), gpuBytes);
            RuntimeDiagnostics.seamPipelineStatus(seamRuntimeMetrics.snapshot().toString());
            draw(context, current);
            RuntimeDiagnostics.renderCallback(meshes.size(), parentMeshes.size());
        } catch (Throwable error) {
            // A driver-specific GLES failure should disable this optional renderer, not crash the client.
            failed = true;
            RuntimeDiagnostics.rendererFailed(error);
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
        RuntimeDiagnostics.shaderReady(program != 0 && mvpLocation >= 0 && cameraLocation >= 0);
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
            MeshBuildData data;
            if (deferredLeafUpload != null) {
                data = deferredLeafUpload;
                deferredLeafUpload = null;
            } else {
                data = scheduler.pollCompleted();
            }
            if (data == null) break;
            VoxelSection latest = current.get(data.source().position());
            if (latest != data.source()
                    || selectedLods.getOrDefault(data.source().position(), data.lodLevel()) != data.lodLevel())
                continue; // stale source/LOD; a fresh request is issued by the next scheduling pass
            if (!data.isEmpty() && !gpuUploadBudget.tryConsume(data.byteSize())) {
                deferredLeafUpload = data;
                break;
            }
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
    private void processParentGeometry(FrustumCuller schedulingFrustum) {
        List<LodNode> nodes = store.lodTree().nodesWithinDistance(contextFreeCameraX, contextFreeCameraY,
                contextFreeCameraZ, maxLodDistance);
        hierarchySelector.retainNodes(nodes);
        Set<ParentGeometryKey> live = new HashSet<>();
        int parentBuildRequests = 0;
        for (LodNode node : nodes) {
            if (node.level() <= 0) continue;
            ParentGeometryKey key = new ParentGeometryKey(node.origin(), node.level());
            live.add(key);
            if (node.hasCurrentGeometry()) continue;
            // Build only the parent level selected for this region's distance band.
            // Other missing levels are deferred; ready ancestors/descendants remain valid fallbacks.
            if (!hierarchySelector.shouldBuildParentGeometry(node,
                    contextFreeCameraX, contextFreeCameraY, contextFreeCameraZ)) continue;
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
            double minX = node.origin().x() * 16.0 - contextFreeCameraX;
            double minY = node.origin().y() * 16.0 - contextFreeCameraY;
            double minZ = node.origin().z() * 16.0 - contextFreeCameraZ;
            double span = node.spanSections() * 16.0;
            boolean inFrustum = schedulingFrustum.isVisible((float) minX, (float) minY, (float) minZ,
                    (float) (minX + span), (float) (minY + span), (float) (minZ + span));
            priority = visibilityStreaming.parentPriority(priority, inFrustum);
            // Coarse parents cover more terrain: schedule them before fine refinement at comparable distance.
            priority = dev.brezze.voxymobile.render.streaming.ParentLodBuildPriority.apply(priority, node.level());
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
            if (!data.isEmpty() && !gpuUploadBudget.tryConsume(data.byteSize())) {
                parentUploadQueue.offer(data);
                break;
            }
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
                indexArray.length, data.byteSize(), vertexArray, indexArray);
    }

    private double contextFreeCameraX, contextFreeCameraY, contextFreeCameraZ;

    /** Evicts distant high-detail allocations first, retaining coarse coverage meshes longer. */
    private void evictDistantMeshes(double cameraX, double cameraY, double cameraZ, SectionPos protectedPosition) {
        gpuMemoryBudget.shouldEvict(gpuBytes);
        long targetBytes = gpuMemoryBudget.evictionTargetBytes();
        while (gpuMemoryBudget.evictionActive() && gpuBytes > targetBytes
                && meshes.size() + parentMeshes.size() > 1) {
            SectionPos farthestLeaf = null;
            ParentGeometryKey farthestParent = null;
            double worstScore = -1.0;
            for (SectionPos pos : meshes.keySet()) {
                if (pos.equals(protectedPosition)) continue;
                double score = GpuMeshEvictionPolicy.score(
                        distanceToSectionAabbSquared(pos, 1, cameraX, cameraY, cameraZ), 0);
                if (score > worstScore) {
                    worstScore = score; farthestLeaf = pos; farthestParent = null;
                }
            }
            for (ParentGeometryKey key : parentMeshes.keySet()) {
                if (key.origin().equals(protectedPosition)) continue;
                double score = GpuMeshEvictionPolicy.score(
                        distanceToSectionAabbSquared(key.origin(), 1 << key.level(), cameraX, cameraY, cameraZ),
                        key.level());
                if (score > worstScore) {
                    worstScore = score; farthestParent = key; farthestLeaf = null;
                }
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
                store.lodTree().invalidateGeometry(farthestParent.origin(), farthestParent.level());
            } else break;
        }
    }

    private static double distanceToSectionAabbSquared(SectionPos origin, int spanSections,
                                                        double cameraX, double cameraY, double cameraZ) {
        double minX = origin.x() * 16.0, minY = origin.y() * 16.0, minZ = origin.z() * 16.0;
        double span = spanSections * 16.0;
        double dx = cameraX < minX ? minX - cameraX : cameraX > minX + span ? cameraX - minX - span : 0.0;
        double dy = cameraY < minY ? minY - cameraY : cameraY > minY + span ? cameraY - minY - span : 0.0;
        double dz = cameraZ < minZ ? minZ - cameraZ : cameraZ > minZ + span ? cameraZ - minZ - span : 0.0;
        return dx * dx + dy * dy + dz * dz;
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
        return new GpuMesh(data.source(), data.lodLevel(), vao, vbo, ebo, indexArray.length, byteSize,
                vertexArray, indexArray);
    }


    /** Thread-safe CPU handoff; actual GL allocation occurs on the next render callback. */
    public boolean enqueueSeamMesh(LodSeamGpuMeshData data) {
        return seamUploadQueue.offer(data);
    }

    private void processSeamUploads() {
        long uploadedBytes = 0L;
        while (uploadedBytes < MAX_SEAM_UPLOAD_BYTES_PER_FRAME) {
            LodSeamGpuMeshData data = seamUploadQueue.poll();
            if (data == null) break;
            if (data.isEmpty()) continue;
            // A newer source revision may have appeared after CPU completion but before this upload.
            if (seamRevisionTracker.currentRevision(data.key()) != data.contentRevision()) { seamRuntimeMetrics.staleResult(); continue; }
            LodSeamGpuBuffer old = seamMeshes.get(data.key());
            if (old != null && old.revision() > data.contentRevision()) { seamRuntimeMetrics.staleResult(); continue; }
            if (uploadedBytes > 0L && data.byteSize() > MAX_SEAM_UPLOAD_BYTES_PER_FRAME - uploadedBytes) {
                seamUploadQueue.offer(data);
                break;
            }
            LodSeamGpuBuffer replacement = LodSeamGpuBuffer.upload(data);
            seamMeshes.put(data.key(), replacement);
            if (old != null) { gpuBytes -= old.byteSize(); old.delete(); }
            gpuBytes += replacement.byteSize();
            uploadedBytes += replacement.byteSize();
            seamRuntimeMetrics.uploadCompleted();
        }
    }

    public boolean removeSeamMesh(LodSeamKey key) {
        if (key == null) return false;
        seamUploadQueue.remove(key);
        seamBuildQueue.remove(key);
        seamRevisionTracker.remove(key);
        seamRetryPolicy.remove(key);
        seamCandidateBounds.remove(key);
        seamFailureMarkers.remove(key);
        LodSeamGpuBuffer removed = seamMeshes.remove(key);
        if (removed == null) return false;
        seamRuntimeMetrics.meshRemoved();
        gpuBytes -= removed.byteSize();
        removed.delete();
        return true;
    }

    /** Builds a bounded number of nearest-camera seam payloads from the active selection. */
    private record SeamBuildCandidate(LodSeamSmartScheduler.Candidate priority,
                                      dev.brezze.voxymobile.render.geometry.LodNeighborBatch.Candidate neighbor) {}

    private void refreshLiveSeams(List<LodNodeTraversal.Selection> selections,
                                  LodSelectionNeighborBridge.Result neighbors,
                                  FrustumCuller frustum,
                                  double cameraX, double cameraY, double cameraZ) {
        Set<LodSeamKey> nextKeys = new HashSet<>();
        Map<LodSeamKey, SeamBuildCandidate> candidatesByKey = new HashMap<>();
        List<LodSeamSmartScheduler.Candidate> schedulingCandidates = new ArrayList<>();
        for (dev.brezze.voxymobile.render.geometry.LodNeighborBatch.Candidate candidate
                : neighbors.batch().candidates()) {
            if (!candidate.requiresSeam()) continue;
            LodNode first = selections.get(candidate.firstRegionIndex).node();
            LodNode second = selections.get(candidate.secondRegionIndex).node();
            LodSeamKey key = new LodSeamKey(regionId(first), regionId(second));
            nextKeys.add(key);
            LodNode fine = first.level() < second.level() ? first : second;
            LodNode coarse = fine == first ? second : first;
            CpuMeshView fineMesh = cpuMesh(fine);
            CpuMeshView coarseMesh = cpuMesh(coarse);
            if (fineMesh == null || coarseMesh == null) { removeSeamMesh(key); continue; }
            long revision = seamRevisionTracker.revisionFor(key,
                    fine.contentRevision(), coarse.contentRevision());
            boolean visible = CameraRelativeAabb.node(first.origin(), first.spanSections(),
                    cameraX, cameraY, cameraZ).isVisible(frustum)
                    || CameraRelativeAabb.node(second.origin(), second.spanSections(),
                    cameraX, cameraY, cameraZ).isVisible(frustum);
            double distance = seamDistanceSquared(candidate, selections, cameraX, cameraY, cameraZ);
            LodSeamSmartScheduler.Candidate priority = new LodSeamSmartScheduler.Candidate(
                    key, distance, visible, true, revision);
            schedulingCandidates.add(priority);
            candidatesByKey.put(key, new SeamBuildCandidate(priority, candidate));
        }
        seamRuntimeMetrics.activeCandidates(schedulingCandidates.size());
        // The exact active render frustum and current mesh revisions gate CPU work; nearest 8 only.
        List<LodSeamSmartScheduler.Candidate> selected = LodSeamSmartScheduler.select(schedulingCandidates, 8);
        for (LodSeamSmartScheduler.Candidate priority : selected) {
            SeamBuildCandidate selectedWork = candidatesByKey.get(priority.key());
            if (selectedWork == null) continue;
            dev.brezze.voxymobile.render.geometry.LodNeighborBatch.Candidate candidate = selectedWork.neighbor();
            LodNode first = selections.get(candidate.firstRegionIndex).node();
            LodNode second = selections.get(candidate.secondRegionIndex).node();
            LodSeamKey key = new LodSeamKey(regionId(first), regionId(second));
            LodNode fine = first.level() < second.level() ? first : second;
            LodNode coarse = fine == first ? second : first;
            CpuMeshView fineMesh = cpuMesh(fine);
            CpuMeshView coarseMesh = cpuMesh(coarse);
            if (fineMesh == null || coarseMesh == null) { removeSeamMesh(key); continue; }
            long revision = priority.revision();
            if (!seamRetryPolicy.shouldAttempt(key, revision, System.nanoTime())) continue;
            LodSeamGpuBuffer existing = seamMeshes.get(key);
            if (existing != null && existing.revision() == revision) continue;
            if (seamUploadQueue.pendingRevision(key) == revision) continue;
            LodNeighborDetector.NeighborPair edge = candidate.neighbor;
            int planeAxis, rangeAxisB;
            if (edge.edge == LodNeighborDetector.Edge.EAST_WEST
                    || edge.edge == LodNeighborDetector.Edge.WEST_EAST) {
                planeAxis = 0; rangeAxisB = 2;
            } else {
                planeAxis = 2; rangeAxisB = 0;
            }
            float minY = (float) Math.max(fine.origin().y() * 16.0, coarse.origin().y() * 16.0);
            float maxY = (float) Math.min((fine.origin().y() + fine.spanSections()) * 16.0,
                    (coarse.origin().y() + coarse.spanSections()) * 16.0);
            float minB = (float) edge.overlapMin, maxB = (float) edge.overlapMax;
            // Snapshot mesh arrays before handing work to the worker; no renderer maps or GL calls
            // are accessed from the CPU worker.
            float[] fineVertices = fineMesh.vertices.clone();
            int[] fineIndices = fineMesh.indices.clone();
            float[] coarseVertices = coarseMesh.vertices.clone();
            int[] coarseIndices = coarseMesh.indices.clone();
            final int pAxis = planeAxis, bAxis = rangeAxisB;
            final float p = (float) edge.plane, y0 = minY, y1 = maxY, b0 = minB, b1 = maxB;
            LodSeamBounds expectedBounds = pAxis == 0
                    ? new LodSeamBounds(p - 0.06f, y0, b0, p + 0.06f, y1, b1)
                    : new LodSeamBounds(b0, y0, p - 0.06f, b1, y1, p + 0.06f);
            seamCandidateBounds.put(key, expectedBounds);
            if (seamBuildQueue.submit(key, revision, () -> LodSeamLiveProducer.generate(key, revision,
                    fineVertices, fineIndices, coarseVertices, coarseIndices,
                    pAxis, p, 1, y0, y1, bAxis, b0, b1, 1.0e-3f, 8192))) {
                // Keep the last failure marker visible while recovery is pending.
                seamRuntimeMetrics.buildScheduled();
            }
        }
        for (LodSeamKey old : new HashSet<>(activeSeamKeys))
            if (!nextKeys.contains(old)) removeSeamMesh(old);
        activeSeamKeys.clear();
        activeSeamKeys.addAll(nextKeys);
    }

    /** Transfers completed CPU results to the render-thread GPU upload queue. */
    private void processCompletedSeamBuilds() {
        LodSeamAsyncBuildQueue.Completed completed;
        int handled = 0;
        while (handled++ < 16 && (completed = seamBuildQueue.poll()) != null) {
            if (completed.result() == null || !completed.result().generated()) {
                seamRuntimeMetrics.buildCompleted(false);
                LodSeamBounds bounds = seamCandidateBounds.get(completed.key());
                if (bounds != null && seamRevisionTracker.currentRevision(completed.key()) == completed.revision()) {
                    seamRetryPolicy.recordFailure(completed.key(), completed.revision(), System.nanoTime());
                    String reason = completed.result() == null ? "worker-exception" : completed.result().status();
                    seamFailureMarkers.put(completed.key(), new LodSeamFailureMarker(
                            completed.key(), completed.revision(), bounds, reason));
                }
                continue;
            }
            seamRuntimeMetrics.buildCompleted(true);
            long currentRevision = seamRevisionTracker.currentRevision(completed.key());
            if (currentRevision != completed.revision()) { seamRuntimeMetrics.staleResult(); continue; }
            seamRetryPolicy.recordSuccess(completed.key(), completed.revision());
            seamFailureMarkers.remove(completed.key());
            seamRuntimeMetrics.uploadQueued(enqueueSeamMesh(completed.result().payload()));
        }
    }

    private static double seamDistanceSquared(
            dev.brezze.voxymobile.render.geometry.LodNeighborBatch.Candidate candidate,
            List<LodNodeTraversal.Selection> selections, double cameraX, double cameraY, double cameraZ) {
        LodNode a = selections.get(candidate.firstRegionIndex).node();
        LodNode b = selections.get(candidate.secondRegionIndex).node();
        double ax = (a.origin().x() + a.spanSections() * 0.5) * 16.0;
        double ay = (a.origin().y() + a.spanSections() * 0.5) * 16.0;
        double az = (a.origin().z() + a.spanSections() * 0.5) * 16.0;
        double bx = (b.origin().x() + b.spanSections() * 0.5) * 16.0;
        double by = (b.origin().y() + b.spanSections() * 0.5) * 16.0;
        double bz = (b.origin().z() + b.spanSections() * 0.5) * 16.0;
        double dx = (ax + bx) * 0.5 - cameraX;
        double dy = (ay + by) * 0.5 - cameraY;
        double dz = (az + bz) * 0.5 - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean hasRenderableMesh(LodNode node) {
        if (node == null || !node.hasCurrentGeometry()) return false;
        if (node.level() == 0) {
            GpuMesh mesh = meshes.get(node.origin());
            return mesh != null && mesh.source == node.source();
        }
        ParentGpuMesh mesh = parentMeshes.get(new ParentGeometryKey(node.origin(), node.level()));
        return mesh != null && mesh.revision == node.contentRevision();
    }

    private CpuMeshView cpuMesh(LodNode node) {
        if (node.level() == 0) {
            GpuMesh mesh = meshes.get(node.origin());
            return mesh == null ? null : new CpuMeshView(mesh.vertices, mesh.indices);
        }
        ParentGpuMesh mesh = parentMeshes.get(new ParentGeometryKey(node.origin(), node.level()));
        if (mesh == null || mesh.revision != node.contentRevision()) return null;
        return new CpuMeshView(mesh.vertices, mesh.indices);
    }

    private static long regionId(LodNode node) {
        long h = 0x9E3779B97F4A7C15L;
        h = (h ^ node.origin().x()) * 0xBF58476D1CE4E5B9L;
        h = (h ^ node.origin().y()) * 0x94D049BB133111EBL;
        h = (h ^ node.origin().z()) * 0x9E3779B97F4A7C15L;
        h = (h ^ node.level()) * 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 31);
    }

    private record CpuMeshView(float[] vertices, int[] indices) {}

    private void draw(WorldRenderContext context, Map<SectionPos, VoxelSection> current) {
        if (meshes.isEmpty() && parentMeshes.isEmpty() && seamMeshes.isEmpty() && seamFailureMarkers.isEmpty()) {
            RuntimeDiagnostics.renderAudit(0, 0, 0, 0);
            RuntimeDiagnostics.drawStats(0, 0);
            RuntimeDiagnostics.seamDrawStats(0, 0);
            return;
        }
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
                frustum, maxLodDistance)) visibleLeaves.add(visible.position());
        int leafDrawCount = 0;
        int parentDrawCount = 0;
        RenderFrameAudit audit = new RenderFrameAudit();
        List<LodNodeTraversal.Selection> activeSelections = LodNodeTraversal.collectByDistance(
                store.lodTree(), hierarchySelector, cameraPos.x, cameraPos.y, cameraPos.z, maxLodDistance,
                this::hasRenderableMesh);
        // Rebuild every ten render frames from the exact node list consumed by this draw pass.
        // The caps bound Android CPU work; truncation is surfaced rather than hidden.
        if (++neighborRefreshTicks >= 10) {
            neighborRefreshTicks = 0;
            LodSelectionNeighborBridge.Result neighbors = LodSelectionNeighborBridge.build(
                    activeSelections, 512, 1024);
            refreshLiveSeams(activeSelections, neighbors, frustum, cameraPos.x, cameraPos.y, cameraPos.z);
            RuntimeDiagnostics.neighborBatch(neighbors.batch().neighborCount(),
                    neighbors.batch().seamCandidateCount(), neighbors.truncated());
        }
        for (LodNodeTraversal.Selection selection : activeSelections) {
            LodNode node = selection.node();
            SectionPos origin = node.origin();
            CameraRelativeAabb nodeBounds = CameraRelativeAabb.node(origin, node.spanSections(),
                    cameraPos.x, cameraPos.y, cameraPos.z);
            audit.candidate();
            if (!nodeBounds.isVisible(frustum)) { audit.frustumRejected(); continue; }
            if (node.level() == 0) {
                if (!visibleLeaves.contains(origin)) { audit.frustumRejected(); continue; }
                GpuMesh mesh = meshes.get(origin);
                if (mesh == null) { audit.missingMesh(); continue; }
                GL30C.glBindVertexArray(mesh.vao);
                GL11C.glDrawElements(GL11C.GL_TRIANGLES, mesh.indexCount, GL11C.GL_UNSIGNED_INT, 0L);
                leafDrawCount++;
                audit.leafDrawn();
            } else {
                ParentGpuMesh mesh = parentMeshes.get(new ParentGeometryKey(origin, node.level()));
                if (mesh == null) { audit.missingMesh(); continue; }
                if (mesh.revision != node.contentRevision()) { audit.staleParent(); continue; }
                GL30C.glBindVertexArray(mesh.vao);
                GL11C.glDrawElements(GL11C.GL_TRIANGLES, mesh.indexCount, GL11C.GL_UNSIGNED_INT, 0L);
                parentDrawCount++;
                audit.parentDrawn();
            }
        }
        int seamDrawCount = 0;
        int seamFrustumRejected = 0;
        for (LodSeamGpuBuffer seam : seamMeshes.values()) {
            if (seam.drawIfVisible(frustum, cameraPos.x, cameraPos.y, cameraPos.z)) seamDrawCount++;
            else seamFrustumRejected++;
        }
        RuntimeDiagnostics.seamDrawStats(seamDrawCount, seamFrustumRejected);
        if (seamDebugOverlayEnabled && (!seamMeshes.isEmpty() || !seamFailureMarkers.isEmpty())) {
            // Debug vertices are already camera-relative; neutralize the shader's world-position subtraction.
            GL20C.glUniform3f(cameraLocation, 0.0f, 0.0f, 0.0f);
            if (!seamMeshes.isEmpty()) drawSeamDebugOverlay(cameraPos.x, cameraPos.y, cameraPos.z);
            if (!seamFailureMarkers.isEmpty()) drawSeamFailureOverlay(cameraPos.x, cameraPos.y, cameraPos.z);
            GL20C.glUniform3f(cameraLocation, (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        }
        RuntimeDiagnostics.renderAudit(audit.candidates(), audit.frustumRejectedCount(),
                audit.missingMeshes(), audit.staleParents());
        RuntimeDiagnostics.drawStats(leafDrawCount, parentDrawCount);
        GL30C.glBindVertexArray(previousVao);
        GL20C.glUseProgram(previousProgram);
        GL11C.glDepthMask(previousDepthMask);
        if (depthEnabled) GL11C.glEnable(GL11C.GL_DEPTH_TEST); else GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        if (cullEnabled) GL11C.glEnable(GL11C.GL_CULL_FACE); else GL11C.glDisable(GL11C.GL_CULL_FACE);
        if (blendEnabled) GL11C.glEnable(GL11C.GL_BLEND); else GL11C.glDisable(GL11C.GL_BLEND);
    }

    private static double distanceToNodeAabbSquared(LodNode node, double x, double y, double z) {
        double minX = node.origin().x() * 16.0, minY = node.origin().y() * 16.0,
                minZ = node.origin().z() * 16.0;
        double span = node.spanSections() * 16.0;
        double dx = x < minX ? minX - x : x > minX + span ? x - (minX + span) : 0.0;
        double dy = y < minY ? minY - y : y > minY + span ? y - (minY + span) : 0.0;
        double dz = z < minZ ? minZ - z : z > minZ + span ? z - (minZ + span) : 0.0;
        return dx * dx + dy * dy + dz * dz;
    }

    public void setMaxLodDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 256.0 || distance > 8192.0)
            throw new IllegalArgumentException("LOD distance must be 256..8192 blocks");
        this.maxLodDistance = distance;
    }

    public double getMaxLodDistance() { return maxLodDistance; }

    /** Enables/disables the in-world orange AABB wireframe for uploaded seam meshes. */
    public void setSeamDebugOverlayEnabled(boolean enabled) { this.seamDebugOverlayEnabled = enabled; }
    public boolean isSeamDebugOverlayEnabled() { return seamDebugOverlayEnabled; }

    private void drawSeamDebugOverlay(double cameraX, double cameraY, double cameraZ) {
        List<dev.brezze.voxymobile.render.geometry.LodSeamBounds> bounds = new ArrayList<>();
        for (LodSeamGpuBuffer seam : seamMeshes.values()) bounds.add(seam.bounds());
        float[] packed = LodSeamDebugOverlay.build(bounds, cameraX, cameraY, cameraZ);
        if (packed.length == 0) return;
        FloatBuffer data = BufferUtils.createFloatBuffer(packed.length).put(packed);
        data.flip();
        if (seamDebugVao == 0) {
            seamDebugVao = GL30C.glGenVertexArrays();
            seamDebugVbo = GL15C.glGenBuffers();
            GL30C.glBindVertexArray(seamDebugVao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, seamDebugVbo);
            int stride = LodSeamDebugOverlay.FLOATS_PER_VERTEX * Float.BYTES;
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, stride, 0L);
            GL20C.glEnableVertexAttribArray(1);
            GL20C.glVertexAttribPointer(1, 4, GL11C.GL_FLOAT, false, stride, 3L * Float.BYTES);
        } else {
            GL30C.glBindVertexArray(seamDebugVao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, seamDebugVbo);
        }
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, data, GL15C.GL_DYNAMIC_DRAW);
        GL11C.glDrawArrays(GL11C.GL_LINES, 0, packed.length / LodSeamDebugOverlay.FLOATS_PER_VERTEX);

        List<float[]> triangleMeshes = new ArrayList<>();
        for (LodSeamGpuBuffer seam : seamMeshes.values()) triangleMeshes.add(seam.copyDebugVertices());
        float[] triangles = LodSeamTriangleVisualizer.combine(triangleMeshes);
        if (triangles.length == 0) return;
        for (int i = 0; i < triangles.length; i += LodSeamTriangleVisualizer.FLOATS_PER_VERTEX) {
            triangles[i] -= (float) cameraX;
            triangles[i + 1] -= (float) cameraY;
            triangles[i + 2] -= (float) cameraZ;
        }
        FloatBuffer triangleData = BufferUtils.createFloatBuffer(triangles.length).put(triangles);
        triangleData.flip();
        GL30C.glBindVertexArray(seamDebugVao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, seamDebugVbo);
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, triangleData, GL15C.GL_DYNAMIC_DRAW);
        GL11C.glDrawArrays(GL11C.GL_LINES, 0, triangles.length / LodSeamTriangleVisualizer.FLOATS_PER_VERTEX);
    }


    /** Draws failed seam candidate bounds in red while F7 diagnostics are enabled. */
    private void drawSeamFailureOverlay(double cameraX, double cameraY, double cameraZ) {
        float[] packed = LodSeamFailureOverlay.build(seamFailureMarkers.values(), cameraX, cameraY, cameraZ);
        if (packed.length == 0) return;
        FloatBuffer data = BufferUtils.createFloatBuffer(packed.length).put(packed);
        data.flip();
        if (seamDebugVao == 0) {
            seamDebugVao = GL30C.glGenVertexArrays();
            seamDebugVbo = GL15C.glGenBuffers();
            GL30C.glBindVertexArray(seamDebugVao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, seamDebugVbo);
            int stride = LodSeamFailureOverlay.FLOATS_PER_VERTEX * Float.BYTES;
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, stride, 0L);
            GL20C.glEnableVertexAttribArray(1);
            GL20C.glVertexAttribPointer(1, 4, GL11C.GL_FLOAT, false, stride, 3L * Float.BYTES);
        } else {
            GL30C.glBindVertexArray(seamDebugVao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, seamDebugVbo);
        }
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, data, GL15C.GL_DYNAMIC_DRAW);
        GL11C.glDrawArrays(GL11C.GL_LINES, 0, packed.length / LodSeamFailureOverlay.FLOATS_PER_VERTEX);
    }

    public boolean isFailed() { return failed; }
    public int leafMeshCount() { return meshes.size(); }
    public int parentMeshCount() { return parentMeshes.size(); }
    public long gpuBytesAllocated() { return gpuBytes; }
    public int seamFailureMarkerCount() { return seamFailureMarkers.size(); }
    public int gpuMemoryPressureLevel() { return gpuMemoryBudget.pressureLevel(gpuBytes); }
    public long gpuUploadBytesThisFrame() { return gpuUploadBudget.uploadedBytesThisFrame(); }

    public void release() {
        for (GpuMesh mesh : meshes.values()) mesh.delete();
        meshes.clear();
        for (ParentGpuMesh mesh : parentMeshes.values()) mesh.delete();
        parentMeshes.clear();
        for (LodSeamGpuBuffer seam : seamMeshes.values()) seam.delete();
        seamMeshes.clear();
        if (seamDebugVao != 0) { GL30C.glDeleteVertexArrays(seamDebugVao); seamDebugVao = 0; }
        if (seamDebugVbo != 0) { GL15C.glDeleteBuffers(seamDebugVbo); seamDebugVbo = 0; }
        seamDebugOverlayEnabled = false;
        activeSeamKeys.clear();
        seamCandidateBounds.clear();
        seamFailureMarkers.clear();
        seamUploadQueue.clear();
        seamBuildQueue.close();
        seamRevisionTracker.clear();
        seamRetryPolicy.clear();
        RuntimeDiagnostics.seamDrawStats(0, 0);
        parentGeometryCache.clear();
        parentUploadQueue.clear();
        deferredLeafUpload = null;
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
        final float[] vertices;
        final int[] indices;
        GpuMesh(VoxelSection source, int lodLevel, int vao, int vbo, int ebo, int indexCount, long byteSize,
                float[] vertices, int[] indices) {
            this.source = source; this.lodLevel = lodLevel;
            this.vao = vao; this.vbo = vbo; this.ebo = ebo; this.indexCount = indexCount; this.byteSize = byteSize;
            this.vertices = vertices; this.indices = indices;
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
        final float[] vertices;
        final int[] indices;
        ParentGpuMesh(ParentGeometryKey key, long revision, int vao, int vbo, int ebo,
                      int indexCount, long byteSize, float[] vertices, int[] indices) {
            this.key = key; this.revision = revision; this.vao = vao; this.vbo = vbo;
            this.ebo = ebo; this.indexCount = indexCount; this.byteSize = byteSize;
            this.vertices = vertices; this.indices = indices;
        }
        void delete() {
            GL30C.glDeleteVertexArrays(vao);
            GL15C.glDeleteBuffers(vbo);
            GL15C.glDeleteBuffers(ebo);
        }
    }

}
