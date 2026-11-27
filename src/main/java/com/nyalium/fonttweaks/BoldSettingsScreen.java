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

/** 「粗体渲染…」子页：启用 / 模式 / 膨胀强度 / 偏移距离。 */
public final class BoldSettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 24;
    private static final int TOP = 56;
    private static final int WIDTH = 260;

    private final Screen parent;

    public BoldSettingsScreen(Screen parent) {
        super(Component.translatable("font_tweaks.config.boldTitle"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int width = Math.min(WIDTH, this.width - 40);
        int x = this.width / 2 - width / 2;
        int y = TOP;
        Config.Cfg cfg = Config.get();
        boolean on = cfg.boldOptimize;
        boolean dilation = cfg.boldMode == Config.MODE_DILATION;

        addRenderableWidget(CycleButton.onOffBuilder(cfg.boldOptimize)
                .create(x, y, width, ROW_HEIGHT, Component.translatable("font_tweaks.config.bold"),
                        (b, v) -> {
                            Config.get().boldOptimize = v;
                            BoldDilation.reset();   // 切换开关时解除“永久停用”，让功能重试
                            this.rebuildWidgets();
                        }));
        y += ROW_GAP;

        addRenderableWidget(CycleButton.<Integer>builder(
                        mode -> Component.translatable(mode == Config.MODE_DILATION
                                ? "font_tweaks.config.mode.dilation" : "font_tweaks.config.mode.offset"),
                        cfg.boldMode)
                .withValues(Config.MODE_DILATION, Config.MODE_OFFSET)
                .create(x, y, width, ROW_HEIGHT, Component.translatable("font_tweaks.config.mode"),
                        (b, v) -> {
                            Config.get().boldMode = v;
                            BoldDilation.reset();
                            this.rebuildWidgets();
                        }));
        y += ROW_GAP;

        AbstractSliderButton dilationSlider = new ValueSlider(x, y, width, ROW_HEIGHT, 0.0, 0.5, 0.05,
                cfg.boldDilation,
                v -> Component.translatable("font_tweaks.config.dilation",
                        String.format(java.util.Locale.ROOT, "%.2f", v)),
                v -> Config.get().boldDilation = v);
        dilationSlider.active = on && dilation;
        addRenderableWidget(dilationSlider);
        y += ROW_GAP;

        AbstractSliderButton offsetSlider = new ValueSlider(x, y, width, ROW_HEIGHT, 0.0, 1.0, 0.05,
                cfg.boldOffset,
                v -> Component.translatable("font_tweaks.config.offset",
                        String.format(java.util.Locale.ROOT, "%.2f", v)),
                v -> Config.get().boldOffset = v);
        offsetSlider.active = on && !dilation;
        addRenderableWidget(offsetSlider);

        addRenderableWidget(Button.builder(Component.translatable("font_tweaks.config.reset"), b -> {
            Config.resetBold();
            this.rebuildWidgets();
        }).bounds(this.width / 2 - 60 - 128, this.height - ROW_HEIGHT - 8, 120, ROW_HEIGHT).build());
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

    private static final class ValueSlider extends AbstractSliderButton {
        private final double min, max, step;
        private final DoubleFunction<Component> label;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int w, int h, double min, double max, double step, double initial,
                    DoubleFunction<Component> label, DoubleConsumer onChange) {
            super(x, y, w, h, Component.empty(), (initial - min) / (max - min));
            this.min = min; this.max = max; this.step = step; this.label = label; this.onChange = onChange;
            updateMessage();
        }

        private double currentValue() {
            double raw = this.min + (this.max - this.min) * this.value;
            double snapped = Math.round(raw / this.step) * this.step;
            return Math.max(this.min, Math.min(this.max, snapped));
        }

        @Override protected void updateMessage() { setMessage(this.label.apply(currentValue())); }
        @Override protected void applyValue() { this.onChange.accept(currentValue()); }

        @Override
        public boolean keyPressed(KeyEvent event) {
            boolean left = event.isLeft(), right = event.isRight();
            if (this.active && (left || right)) {
                double f = this.step / (this.max - this.min);
                setValue(Math.max(0.0, Math.min(1.0, this.value + (left ? -f : f))));
                return true;
            }
            return super.keyPressed(event);
        }
    }
}
