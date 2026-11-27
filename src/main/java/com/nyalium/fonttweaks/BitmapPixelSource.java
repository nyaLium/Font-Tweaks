package com.nyalium.fonttweaks;

import com.mojang.blaze3d.platform.NativeImage;
import org.jspecify.annotations.Nullable;

/**
 * 位图字体（{@code BitmapProvider}）的像素来源：从整张字形大图里裁出当前字形那一格。
 *
 * <p>延迟到第一次画粗体时才裁 —— 那时 provider 还活着（和字形同生命周期），
 * 万一已经关了 {@link BoldDilation#imageOf} 会返回 {@code null}，上层自动退回原版。</p>
 */
public final class BitmapPixelSource implements BoldPixelSource {
    private final Object glyph;
    private final int sourceX;
    private final int sourceY;
    private final int width;
    private final int height;

    public BitmapPixelSource(Object glyph, int sourceX, int sourceY, int width, int height) {
        this.glyph = glyph;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.width = width;
        this.height = height;
    }

    @Override
    public @Nullable NativeImage render() {
        NativeImage image = BoldDilation.imageOf(this.glyph);
        if (image == null) {
            return null;
        }

        return BoldDilation.crop(image, this.sourceX, this.sourceY, this.width, this.height);
    }
}
