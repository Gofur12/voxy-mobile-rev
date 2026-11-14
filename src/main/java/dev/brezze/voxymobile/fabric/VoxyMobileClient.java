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
import org.lwjgl.glfw.GLFW;
import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.util.ArrayDeque;
import java.util.Queue;

/** Client chunk ingestion and Stage 05 GLES terrain LOD rendering entrypoint. */
public final class VoxyMobileClient implements ClientModInitializer {
    private static final ChunkSnapshotStore STORE = new ChunkSnapshotStore();
    private static final Queue<PendingSection> PENDING = new ArrayDeque<>();
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
            synchronized (PENDING) { PENDING.removeIf(p -> p.chunkX == chunk.getPos().x && p.chunkZ == chunk.getPos().z); }
            RuntimeDiagnostics.chunkUnloaded();
            STORE.removeChunk(chunk.getPos().x, chunk.getPos().z);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_SEAM_DEBUG.wasPressed()) {
                RENDERER.setSeamDebugOverlayEnabled(!RENDERER.isSeamDebugOverlayEnabled());
                System.out.println("[VoxyMobile] seam debug overlay=" + RENDERER.isSeamDebugOverlayEnabled());
            }
            // One section per tick to avoid a large synchronous scan on mobile.
            PendingSection pending;
            synchronized (PENDING) { pending = PENDING.poll(); }
            if (pending != null && client.world != null && client.world == pending.world
                    && client.world.getChunkManager().getChunk(
                            pending.chunkX, pending.chunkZ, ChunkStatus.FULL, false) == pending.chunk) {
                STORE.put(capture(pending));
                RuntimeDiagnostics.sectionCaptured();
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
        synchronized (PENDING) {
            for (int i = 0; i < sections.length; i++) {
                if (!sections[i].isEmpty()) {
                    PENDING.add(new PendingSection(world, chunk, sections[i],
                            chunk.getPos().x, bottomSectionY + i, chunk.getPos().z));
                    queued++;
                }
            }
        }
        RuntimeDiagnostics.chunkLoaded(queued);
    }

    private static dev.brezze.voxymobile.core.voxel.VoxelSection capture(PendingSection p) {
        int[] ids = new int[4096];
        int i = 0;
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            ids[i++] = Block.getRawIdFromState(p.section.getBlockState(x, y, z));
        return SectionSnapshotBuilder.build(new SectionPos(p.chunkX, p.sectionY, p.chunkZ), ids);
    }

    public static ChunkSnapshotStore store() { return STORE; }
    public static void setSeamDebugOverlayEnabled(boolean enabled) { RENDERER.setSeamDebugOverlayEnabled(enabled); }

    private record PendingSection(ClientWorld world, WorldChunk chunk, ChunkSection section,
                                  int chunkX, int sectionY, int chunkZ) {}
}
