package com.nyalium.fonttweaks;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Font Tweaks 的入口页：粗体渲染 / 荧光文本渲染 两个子页。 */
public final class FontTweaksScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 24;
    private static final int TOP = 56;
    private static final int WIDTH = 260;

    private final Screen parent;

    public FontTweaksScreen(Screen parent) {
        super(Component.translatable("font_tweaks.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = Math.min(WIDTH, this.width - 40);
        int x = this.width / 2 - width / 2;
        int y = TOP;

        addRenderableWidget(Button.builder(Component.translatable("font_tweaks.config.boldEntry"),
                        b -> this.minecraft.gui.setScreen(new BoldSettingsScreen(this)))
                .bounds(x, y, width, ROW_HEIGHT).build());
        y += ROW_GAP;
        addRenderableWidget(Button.builder(Component.translatable("font_tweaks.config.glowEntry"),
                        b -> this.minecraft.gui.setScreen(new GlowSettingsScreen(this)))
                .bounds(x, y, width, ROW_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.translatable("font_tweaks.config.done"), b -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - ROW_HEIGHT - 8, 120, ROW_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 24, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        Config.save();
        this.minecraft.gui.setScreen(this.parent);
    }
}
