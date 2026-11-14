package dev.brezze.voxymobile.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Voxy PC-inspired configuration layout: General and Rendering pages with grouped options.
 * Only controls backed by a working mobile runtime setting are exposed.
 */
public final class VoxyMobileConfigScreen extends Screen {
    private final Screen parent;
    private Page page = Page.GENERAL;
    private LodSlider distanceSlider;
    private ThreadSlider threadSlider;
    private GpuBudgetSlider gpuBudgetSlider;

    private enum Page { GENERAL, RENDERING }

    public VoxyMobileConfigScreen(Screen parent) {
        super(Text.translatable("voxymobile.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        distanceSlider = null;
        threadSlider = null;
        gpuBudgetSlider = null;
        int center = width / 2;
        int panelWidth = Math.min(440, width - 24);
        int left = center - panelWidth / 2;

        addDrawableChild(ButtonWidget.builder(Text.translatable("voxymobile.config.general"), b -> {
            page = Page.GENERAL;
            init();
        }).dimensions(center - 154, 30, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("voxymobile.config.rendering"), b -> {
            page = Page.RENDERING;
            init();
        }).dimensions(center + 4, 30, 150, 20).build());

        if (page == Page.GENERAL) {
            addToggle(left, 65, panelWidth, "voxymobile.config.enabled",
                    VoxyMobileClient.isEnabled(), VoxyMobileClient::setEnabled);
            threadSlider = new ThreadSlider(left + 8, 164, panelWidth - 16, 20, LodDistanceConfig.serviceThreads());
            addDrawableChild(threadSlider);
            addToggle(left, 218, panelWidth, "voxymobile.config.terrain_ingestion",
                    VoxyMobileClient.isTerrainIngestionEnabled(), VoxyMobileClient::setTerrainIngestionEnabled);
        } else {
            addToggle(left, 65, panelWidth, "voxymobile.config.renderer",
                    VoxyMobileClient.isRendererSettingEnabled(), VoxyMobileClient::setRendererEnabled);
            distanceSlider = new LodSlider(left + 8, 173, panelWidth - 16, 20, LodDistanceConfig.distance());
            addDrawableChild(distanceSlider);
            gpuBudgetSlider = new GpuBudgetSlider(left + 8, 237, panelWidth - 16, 20, LodDistanceConfig.gpuMemoryBudgetMb());
            addDrawableChild(gpuBudgetSlider);
            addToggle(left, 264, panelWidth, "voxymobile.config.seam_debug",
                    VoxyMobileClient.isSeamDebugOverlayEnabled(), VoxyMobileClient::setSeamDebugOverlayEnabled);
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(center - 100, height - 30, 200, 20).build());
    }

    private void addToggle(int x, int y, int width, String key, boolean value,
                           java.util.function.Consumer<Boolean> setter) {
        addDrawableChild(ButtonWidget.builder(toggleText(key, value), b -> {
            setter.accept(!value);
            init();
        }).dimensions(x + 8, y + 22, width - 16, 20).build());
    }

    private static Text toggleText(String key, boolean value) {
        return Text.translatable(key).append(": ")
                .append(Text.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int center = width / 2;
        int panelWidth = Math.min(440, width - 24);
        int left = center - panelWidth / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, center, 12, 0xFFFFFF);
        drawGroup(context, left, 58, panelWidth, 57,
                page == Page.GENERAL ? "voxymobile.config.group.general" : "voxymobile.config.group.renderer");
        if (page == Page.GENERAL) {
            drawGroup(context, left, 125, panelWidth, 66, "voxymobile.config.group.threads");
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("voxymobile.config.service_threads",
                    threadSlider == null ? LodDistanceConfig.serviceThreads() : threadSlider.threads()), center, 149, 0xFFFFFF);
            drawGroup(context, left, 202, panelWidth, 57, "voxymobile.config.group.terrain");
        } else {
            drawGroup(context, left, 126, panelWidth, 186, "voxymobile.config.group.distance");
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("voxymobile.config.lod_distance", distanceSlider == null
                            ? LodDistanceConfig.distance() : distanceSlider.distance()),
                    center, 151, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("voxymobile.config.gpu_memory", gpuBudgetSlider == null
                            ? LodDistanceConfig.gpuMemoryBudgetMb() : gpuBudgetSlider.megabytes()),
                    center, 215, 0xFFFFFF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawGroup(DrawContext context, int x, int y, int width, int height, String titleKey) {
        context.fill(x, y, x + width, y + height, 0x88000000);
        context.drawBorder(x, y, width, height, 0xFF777777);
        context.drawTextWithShadow(textRenderer, Text.translatable(titleKey), x + 9, y + 6, 0xFFFFFF);
    }

    @Override
    public void close() {
        if (distanceSlider != null) VoxyMobileClient.applyLodDistance(distanceSlider.distance());
        if (threadSlider != null) LodDistanceConfig.setServiceThreads(threadSlider.threads());
        if (gpuBudgetSlider != null) VoxyMobileClient.setGpuMemoryBudgetMb(gpuBudgetSlider.megabytes());
        if (client != null) client.setScreen(parent);
    }

    private static final class ThreadSlider extends SliderWidget {
        private int threads;
        ThreadSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.empty(), (double)(initial - 1) / 3.0);
            threads = Math.max(1, Math.min(4, initial)); updateMessage();
        }
        int threads() { return threads; }
        @Override protected void updateMessage() { setMessage(Text.translatable("voxymobile.config.service_threads", threads)); }
        @Override protected void applyValue() { threads = Math.max(1, Math.min(4, 1 + (int)Math.round(value * 3.0))); }
    }

    private static final class GpuBudgetSlider extends SliderWidget {
        private int megabytes;
        GpuBudgetSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.empty(),
                    (double)(dev.brezze.voxymobile.core.config.GpuMemoryBudgetPolicy.normalize(initial) - 32) / 224.0);
            megabytes = dev.brezze.voxymobile.core.config.GpuMemoryBudgetPolicy.normalize(initial);
            updateMessage();
        }
        int megabytes() { return megabytes; }
        @Override protected void updateMessage() {
            setMessage(Text.translatable("voxymobile.config.gpu_memory", megabytes));
        }
        @Override protected void applyValue() {
            int raw = 32 + (int)Math.round(value * 224.0 / 16.0) * 16;
            megabytes = dev.brezze.voxymobile.core.config.GpuMemoryBudgetPolicy.normalize(raw);
        }
    }

    private static final class LodSlider extends SliderWidget {
        private int distance;

        LodSlider(int x, int y, int width, int height, int initial) {
            super(x, y, width, height, Text.empty(), (double)(initial - 256) / (8192 - 256));
            this.distance = initial;
            updateMessage();
        }

        int distance() { return distance; }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("voxymobile.config.lod_distance", distance));
        }

        @Override
        protected void applyValue() {
            int raw = 256 + (int)Math.round(value * (8192 - 256) / 256.0) * 256;
            distance = Math.max(256, Math.min(8192, raw));
        }
    }
}
