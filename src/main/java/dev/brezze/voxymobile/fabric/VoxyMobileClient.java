package dev.brezze.voxymobile.fabric;

import dev.brezze.voxymobile.core.ingest.ChunkSnapshotStore;
import dev.brezze.voxymobile.core.ingest.SectionSnapshotBuilder;
import dev.brezze.voxymobile.core.ingest.PersistentSectionCacheWorker;
import dev.brezze.voxymobile.core.ingest.LiveSectionRestoreGate;
import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.render.GlesLodRenderer;
import dev.brezze.voxymobile.diagnostics.RuntimeDiagnostics;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Client chunk ingestion and Stage 05 GLES terrain LOD rendering entrypoint. */
public final class VoxyMobileClient implements ClientModInitializer {
    private static final ChunkSnapshotStore STORE = new ChunkSnapshotStore();
    private static final int MAX_PENDING_SECTIONS = 8192;
    private static final Map<SectionPos, PendingSection> PENDING = new LinkedHashMap<>();
    private static GlesLodRenderer RENDERER;
    private static KeyBinding TOGGLE_SEAM_DEBUG;
    private static PersistentSectionCacheWorker CACHE_WORKER;
    private static ClientWorld CACHE_WORLD;
    private static final LiveSectionRestoreGate RESTORE_GATE = new LiveSectionRestoreGate();
    private static volatile boolean masterEnabled = true;
    private static volatile boolean terrainIngestionSetting = true;
    private static volatile boolean terrainIngestionEnabled = true;
    private static volatile boolean rendererSetting = true;

    @Override public void onInitializeClient() {
        System.out.println("[VoxyMobile] client initializer active; waiting for chunk and world-render events");
        int lodDistance = LodDistanceConfig.load();
        RENDERER = new GlesLodRenderer(STORE, LodDistanceConfig.serviceThreads());
        RENDERER.setMaxLodDistance(lodDistance);
        RENDERER.setGpuMemoryBudgetMb(LodDistanceConfig.gpuMemoryBudgetMb());
        masterEnabled = LodDistanceConfig.enabled();
        rendererSetting = LodDistanceConfig.rendererEnabled();
        terrainIngestionSetting = LodDistanceConfig.terrainIngestionEnabled();
        RENDERER.setEnabled(masterEnabled && rendererSetting);
        RENDERER.setSeamDebugOverlayEnabled(LodDistanceConfig.seamDebugOverlayEnabled());
        terrainIngestionEnabled = masterEnabled && terrainIngestionSetting;
        System.out.println("[VoxyMobile] independent LOD distance=" + lodDistance + " blocks; parent service threads=" + LodDistanceConfig.serviceThreads());
        WorldRenderEvents.AFTER_ENTITIES.register(RENDERER::render);
        TOGGLE_SEAM_DEBUG = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voxymobile.toggle_seam_debug", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7,
                "category.voxymobile"));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> enqueueChunk(world, chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            synchronized (PENDING) { PENDING.entrySet().removeIf(e -> e.getValue().chunkX == chunk.getPos().x && e.getValue().chunkZ == chunk.getPos().z); }
            RuntimeDiagnostics.chunkUnloaded();
            int retained = STORE.retainChunk(chunk.getPos().x, chunk.getPos().z);
            System.out.println("[VoxyMobile] retained " + retained + " section snapshots after chunk unload");
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != CACHE_WORLD) {
                if (CACHE_WORKER != null) CACHE_WORKER.close();
                CACHE_WORKER = null;
                CACHE_WORLD = client.world;
                STORE.clear();
                RESTORE_GATE.clear();
                if (client.world != null) {
                    String identity;
                    if (client.getCurrentServerEntry() != null) identity = "server:" + client.getCurrentServerEntry().address;
                    else if (client.getServer() != null) identity = "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
                    else identity = "unknown-local-world";
                    String worldKey = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
                    String dimensionKey = client.world.getRegistryKey().getValue().toString();
                    Path cacheRoot = FabricLoader.getInstance().getConfigDir().resolve("voxymobile").resolve("terrain-cache");
                    CACHE_WORKER = new PersistentSectionCacheWorker(cacheRoot, worldKey, dimensionKey);
                    double restoreX = client.player != null ? client.player.getX() : 0.0;
                    double restoreY = client.player != null ? client.player.getY() : 0.0;
                    double restoreZ = client.player != null ? client.player.getZ() : 0.0;
                    CACHE_WORKER.load(restoreX, restoreY, restoreZ);
                    System.out.println("[VoxyMobile] persistent terrain cache opened for " + dimensionKey);
                }
            }
            if (CACHE_WORKER != null) {
                for (int i = 0; i < 8; i++) {
                    dev.brezze.voxymobile.core.voxel.VoxelSection restored = CACHE_WORKER.pollLoaded();
                    if (restored == null) break;
                    if (RESTORE_GATE.shouldRestore(restored.position())) STORE.put(restored);
                }
            }
            while (TOGGLE_SEAM_DEBUG.wasPressed()) {
                RENDERER.setSeamDebugOverlayEnabled(!RENDERER.isSeamDebugOverlayEnabled());
                System.out.println("[VoxyMobile] seam debug overlay=" + RENDERER.isSeamDebugOverlayEnabled());
            }
            // One deduplicated section snapshot per tick keeps ingestion bounded on mobile.
            PendingSection pending = null;
            synchronized (PENDING) {
                if (!PENDING.isEmpty()) {
                    Map.Entry<SectionPos, PendingSection> first = PENDING.entrySet().iterator().next();
                    pending = first.getValue();
                    PENDING.remove(first.getKey());
                }
            }
            if (pending != null && client.world != null && client.world == pending.world
                    && client.world.getChunkManager().getChunk(
                            pending.chunkX, pending.chunkZ, ChunkStatus.FULL, false) == pending.chunk) {
                int sectionIndex = pending.sectionY - client.world.getBottomSectionCoord();
                ChunkSection[] currentSections = pending.chunk.getSectionArray();
                if (sectionIndex >= 0 && sectionIndex < currentSections.length) {
                    ChunkSection currentSection = currentSections[sectionIndex];
                    SectionPos position = new SectionPos(pending.chunkX, pending.sectionY, pending.chunkZ);
                    RESTORE_GATE.markLive(position);
                    if (currentSection.isEmpty()) {
                        STORE.removeSection(position);
                        if (CACHE_WORKER != null) CACHE_WORKER.remove(position);
                    } else {
                        dev.brezze.voxymobile.core.voxel.VoxelSection snapshot = capture(pending, currentSection);
                        STORE.put(snapshot);
                        if (CACHE_WORKER != null) CACHE_WORKER.save(snapshot);
                    }
                    RuntimeDiagnostics.sectionCaptured();
                }
            }
            if (CACHE_WORKER != null && CACHE_WORKER.isLoadComplete()
                    && CACHE_WORKER.pendingLoaded() == 0 && isPendingEmpty()) RESTORE_GATE.clear();
            RuntimeDiagnostics.logIfDue(System.nanoTime());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            synchronized (PENDING) { PENDING.clear(); }
            STORE.clear();
            RESTORE_GATE.clear();
            if (CACHE_WORKER != null) CACHE_WORKER.close();
            CACHE_WORKER = null;
            CACHE_WORLD = null;
        });
    }

    private static void enqueueChunk(ClientWorld world, WorldChunk chunk) {
        if (!terrainIngestionEnabled) return;
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSectionY = world.getBottomSectionCoord();
        int queued = 0;
        for (int i = 0; i < sections.length; i++) {
            // Queue empty sections too: an old disk snapshot must be invalidated
            // when the current live chunk confirms that section is now empty.
            enqueueSection(world, chunk, bottomSectionY + i);
            queued++;
        }
        RuntimeDiagnostics.chunkLoaded(queued);
    }

    /** Called after a successful client-side block update. */
    public static void onBlockStateChanged(ClientWorld world, BlockPos pos) {
        if (!terrainIngestionEnabled) return;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) return;
        int sectionY = Math.floorDiv(pos.getY(), 16);
        int sectionIndex = sectionY - world.getBottomSectionCoord();
        if (sectionIndex < 0 || sectionIndex >= chunk.getSectionArray().length) return;
        enqueueSection(world, chunk, sectionY);
    }

    private static boolean isPendingEmpty() {
        synchronized (PENDING) { return PENDING.isEmpty(); }
    }

    private static void enqueueSection(ClientWorld world, WorldChunk chunk, int sectionY) {
        SectionPos key = new SectionPos(chunk.getPos().x, sectionY, chunk.getPos().z);
        synchronized (PENDING) {
            // Coalesce rapid edits to the same section into one latest snapshot.
            PENDING.remove(key);
            if (PENDING.size() >= MAX_PENDING_SECTIONS) {
                SectionPos oldest = PENDING.keySet().iterator().next();
                PENDING.remove(oldest);
            }
            PENDING.put(key, new PendingSection(world, chunk,
                    chunk.getPos().x, sectionY, chunk.getPos().z));
        }
    }

    private static dev.brezze.voxymobile.core.voxel.VoxelSection capture(PendingSection p, ChunkSection section) {
        int[] ids = new int[4096];
        int i = 0;
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            ids[i++] = Block.getRawIdFromState(section.getBlockState(x, y, z));
        return SectionSnapshotBuilder.build(new SectionPos(p.chunkX, p.sectionY, p.chunkZ), ids);
    }

    public static ChunkSnapshotStore store() { return STORE; }
    public static void applyLodDistance(int distance) {
        int normalized = dev.brezze.voxymobile.core.config.LodDistancePolicy.normalize(distance);
        LodDistanceConfig.setDistance(normalized);
        RENDERER.setMaxLodDistance(normalized);
    }
    public static void setEnabled(boolean enabled) {
        masterEnabled = enabled;
        LodDistanceConfig.setEnabled(enabled);
        RENDERER.setEnabled(masterEnabled && rendererSetting);
        terrainIngestionEnabled = masterEnabled && terrainIngestionSetting;
    }
    public static boolean isEnabled() { return masterEnabled; }
    public static void setRendererEnabled(boolean enabled) {
        rendererSetting = enabled;
        LodDistanceConfig.setRendererEnabled(enabled);
        RENDERER.setEnabled(masterEnabled && rendererSetting);
    }
    public static boolean isRendererSettingEnabled() { return rendererSetting; }
    public static void setTerrainIngestionEnabled(boolean enabled) {
        terrainIngestionSetting = enabled;
        terrainIngestionEnabled = masterEnabled && terrainIngestionSetting;
        LodDistanceConfig.setTerrainIngestionEnabled(enabled);
    }
    public static boolean isTerrainIngestionEnabled() { return terrainIngestionSetting; }
    public static void setGpuMemoryBudgetMb(int megabytes) {
        int normalized = dev.brezze.voxymobile.core.config.GpuMemoryBudgetPolicy.normalize(megabytes);
        LodDistanceConfig.setGpuMemoryBudgetMb(normalized);
        RENDERER.setGpuMemoryBudgetMb(normalized);
    }
    public static int gpuMemoryBudgetMb() { return LodDistanceConfig.gpuMemoryBudgetMb(); }
    public static void setSeamDebugOverlayEnabled(boolean enabled) {
        LodDistanceConfig.setSeamDebugOverlayEnabled(enabled);
        RENDERER.setSeamDebugOverlayEnabled(enabled);
    }
    public static boolean isRendererEnabled() { return RENDERER.isEnabled(); }
    public static boolean isSeamDebugOverlayEnabled() { return RENDERER.isSeamDebugOverlayEnabled(); }

    private record PendingSection(ClientWorld world, WorldChunk chunk,
                                  int chunkX, int sectionY, int chunkZ) {}
}
