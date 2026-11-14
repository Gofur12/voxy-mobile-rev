package dev.brezze.voxymobile.fabric;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** Voxy-style two-page settings screen exposed from Mod Menu. */
public final class VoxyMobileConfigScreen extends Screen {
    private final Screen parent;
    private Page page = Page.GENERAL;
    private LodSlider distanceSlider;

    private enum Page { GENERAL, RENDERING }

    public VoxyMobileConfigScreen(Screen parent) {
        super(Text.translatable("voxymobile.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        int center = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("voxymobile.config.general"), b -> {
            page = Page.GENERAL;
            init();
        }).dimensions(center - 154, 30, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("voxymobile.config.rendering"), b -> {
            page = Page.RENDERING;
            init();
        }).dimensions(center + 4, 30, 150, 20).build());

        if (page == Page.GENERAL) {
            distanceSlider = new LodSlider(center - 150, 70, 300, 20, LodDistanceConfig.distance());
            addDrawableChild(distanceSlider);
            addDrawableChild(ButtonWidget.builder(toggleText("voxymobile.config.terrain_ingestion",
                    VoxyMobileClient.isTerrainIngestionEnabled()), b -> {
                VoxyMobileClient.setTerrainIngestionEnabled(!VoxyMobileClient.isTerrainIngestionEnabled());
                init();
            }).dimensions(center - 150, 104, 300, 20).build());
        } else {
            addDrawableChild(ButtonWidget.builder(toggleText("voxymobile.config.renderer",
                    VoxyMobileClient.isRendererEnabled()), b -> {
                VoxyMobileClient.setRendererEnabled(!VoxyMobileClient.isRendererEnabled());
                init();
            }).dimensions(center - 150, 70, 300, 20).build());
            addDrawableChild(ButtonWidget.builder(toggleText("voxymobile.config.seam_debug",
                    VoxyMobileClient.isSeamDebugOverlayEnabled()), b -> {
                VoxyMobileClient.setSeamDebugOverlayEnabled(!VoxyMobileClient.isSeamDebugOverlayEnabled());
                init();
            }).dimensions(center - 150, 96, 300, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(center - 100, height - 30, 200, 20).build());
    }

    private static Text toggleText(String key, boolean value) {
        return Text.translatable(key).append(": ")
                .append(Text.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        if (page == Page.GENERAL && distanceSlider != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("voxymobile.config.lod_distance", distanceSlider.distance()),
                    width / 2, 57, 0xFFFFFF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (distanceSlider != null) VoxyMobileClient.applyLodDistance(distanceSlider.distance());
        if (client != null) client.setScreen(parent);
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
