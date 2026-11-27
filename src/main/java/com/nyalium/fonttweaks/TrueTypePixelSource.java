package com.nyalium.fonttweaks;

import com.mojang.blaze3d.platform.NativeImage;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.freetype.FT_Face;

/**
 * TTF 字体（{@code TrueTypeGlyphProvider}）的像素来源。
 *
 * <p>原版在 {@code upload} 里也是这么干的：临时开一张 {@code LUMINANCE} 位图，
 * 用 {@link NativeImage#copyFromFont} 让 FreeType 现场渲一遍，写完纹理立刻关掉。
 * 这里做同样的事，只是渲完先做膨胀再上纹理。</p>
 *
 * <p>{@code oversample}（常见 3）意味着一个 GUI 像素 = 3 个纹素，
 * 膨胀半径与边距都由 {@link BoldDilation#marginFor} 换算，观感才和位图字体一致。</p>
 */
public final class TrueTypePixelSource implements BoldPixelSource {
    private final Object glyph;
    private final int width;
    private final int height;
    private final int index;

    public TrueTypePixelSource(Object glyph, int width, int height, int index) {
        this.glyph = glyph;
        this.width = width;
        this.height = height;
        this.index = index;
    }

    @Override
    public @Nullable NativeImage render() {
        FT_Face face = BoldDilation.trueTypeFace(this.glyph);
        if (face == null) {
            return null;
        }

        NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, this.width, this.height, false);

        try {
            if (!image.copyFromFont(face, this.index)) {
                image.close();
                return null;
            }

            return image;
        } catch (Throwable t) {
            image.close();
            BoldDilation.disable("TTF 字形取像素失败", t);
            return null;
        }
    }
}
