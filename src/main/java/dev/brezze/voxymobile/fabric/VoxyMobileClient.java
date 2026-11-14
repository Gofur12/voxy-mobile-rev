package dev.brezze.voxymobile.fabric;

import dev.brezze.voxymobile.core.ingest.ChunkSnapshotStore;
import dev.brezze.voxymobile.core.ingest.SectionSnapshotBuilder;
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

/** Client chunk ingestion and Stage 05 GLES terrain LOD rendering entrypoint. */
public final class VoxyMobileClient implements ClientModInitializer {
    private static final ChunkSnapshotStore STORE = new ChunkSnapshotStore();
    private static final int MAX_PENDING_SECTIONS = 8192;
    private static final Map<SectionPos, PendingSection> PENDING = new LinkedHashMap<>();
    private static final GlesLodRenderer RENDERER = new GlesLodRenderer(STORE);
    private static KeyBinding TOGGLE_SEAM_DEBUG;

    @Override public void onInitializeClient() {
        System.out.println("[VoxyMobile] client initializer active; waiting for chunk and world-render events");
        WorldRenderEvents.AFTER_ENTITIES.register(RENDERER::render);
        TOGGLE_SEAM_DEBUG = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voxymobile.toggle_seam_debug", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7,
                "category.voxymobile"));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> enqueueChunk(world, chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            synchronized (PENDING) { PENDING.entrySet().removeIf(e -> e.getValue().chunkX == chunk.getPos().x && e.getValue().chunkZ == chunk.getPos().z); }
            RuntimeDiagnostics.chunkUnloaded();
            STORE.removeChunk(chunk.getPos().x, chunk.getPos().z);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
                    if (currentSection.isEmpty()) STORE.removeSection(position);
                    else STORE.put(capture(pending, currentSection));
                    RuntimeDiagnostics.sectionCaptured();
                }
            }
            RuntimeDiagnostics.logIfDue(System.nanoTime());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            synchronized (PENDING) { PENDING.clear(); }
            STORE.clear();
        });
    }

    private static void enqueueChunk(ClientWorld world, WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSectionY = world.getBottomSectionCoord();
        int queued = 0;
        for (int i = 0; i < sections.length; i++) {
            if (!sections[i].isEmpty()) {
                enqueueSection(world, chunk, bottomSectionY + i);
                queued++;
            }
        }
        RuntimeDiagnostics.chunkLoaded(queued);
    }

    /** Called after a successful client-side block update. */
    public static void onBlockStateChanged(ClientWorld world, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) return;
        int sectionY = Math.floorDiv(pos.getY(), 16);
        int sectionIndex = sectionY - world.getBottomSectionCoord();
        if (sectionIndex < 0 || sectionIndex >= chunk.getSectionArray().length) return;
        enqueueSection(world, chunk, sectionY);
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
    public static void setSeamDebugOverlayEnabled(boolean enabled) { RENDERER.setSeamDebugOverlayEnabled(enabled); }

    private record PendingSection(ClientWorld world, WorldChunk chunk,
                                  int chunkX, int sectionY, int chunkZ) {}
}
