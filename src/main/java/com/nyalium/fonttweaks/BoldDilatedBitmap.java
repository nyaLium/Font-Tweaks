package com.nyalium.fonttweaks;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;

/**
 * 一个「膨胀字形」的位图描述：尺寸比原字形四周各大 {@code margin} 纹素，基准点相应外移，
 * 于是<b>上屏位置与原字形完全一致</b>，只是多了一圈加粗的像素。
 *
 * <p>所有几何量都从原 {@link GlyphBitmap} 推导（{@code oversample} / {@code getLeft()} /
 * {@code getTop()}），所以位图字体和 oversample=3 的 TTF 都对得上：
 * {@code FontTexture.add} 会按 {@link #getPixelWidth()} 重新分槽，再用
 * {@link #getLeft()} / {@link #getTop()} 算四边形边界与 UV。</p>
 *
 * <p>膨胀半径分两种来源：</p>
 * <ul>
 *   <li>{@link #DYNAMIC}（粗体用）：实时读配置 —— 拖强度滑块能立刻重做；</li>
 *   <li>固定值（告示牌描边用：正好 1 像素）：不随配置变化，一次烘焙长期有效。</li>
 * </ul>
 */
public final class BoldDilatedBitmap implements GlyphBitmap {
    /** 强度跟着配置走（粗体）。 */
    public static final double DYNAMIC = -1.0;
    /** 强度取「描边粗细」（荧光描边）。 */
    public static final double GLOW = Double.NaN;

    private final GlyphBitmap base;
    private final BoldPixelSource pixels;
    private final double strength;
    private final boolean square;

    public BoldDilatedBitmap(GlyphBitmap base, BoldPixelSource pixels) {
        this(base, pixels, DYNAMIC);
    }

    public BoldDilatedBitmap(GlyphBitmap base, BoldPixelSource pixels, double strength) {
        this(base, pixels, strength, false);
    }

    /**
     * @param square {@code true} = 方形膨胀（3×3 邻域满覆盖，告示牌荧光描边用）；
     *               {@code false} = 圆盘膨胀（笔画边缘半像素过渡，粗体用）
     */
    public BoldDilatedBitmap(GlyphBitmap base, BoldPixelSource pixels, double strength, boolean square) {
        this.base = base;
        this.pixels = pixels;
        this.strength = strength;
        this.square = square;
    }

    @Override
    public int getPixelWidth() {
        return this.base.getPixelWidth() + 2 * this.margin();
    }

    @Override
    public int getPixelHeight() {
        return this.base.getPixelHeight() + 2 * this.margin();
    }

    @Override
    public float getOversample() {
        return this.base.getOversample();
    }

    @Override
    public boolean isColored() {
        return this.base.isColored();
    }

    @Override
    public float getLeft() {
        return this.base.getLeft() - this.margin() / this.getOversample();
    }

    @Override
    public float getTop() {
        return this.base.getTop() - this.margin() / this.getOversample();
    }

    @Override
    public float getBearingLeft() {
        return this.base.getBearingLeft() + this.margin() / this.getOversample();
    }

    @Override
    public float getBearingTop() {
        return this.base.getBearingTop() + this.margin() / this.getOversample();
    }

    /** 当前生效的膨胀强度（GUI 像素）。 */
    private float strength() {
        if (Double.isNaN(this.strength)) return SignGlowOutline.width();
        return this.strength == DYNAMIC ? BoldDilation.strength() : (float) this.strength;
    }

    /** 四周各扩多少纹素（随 oversample 与强度变化）。 */
    private int margin() {
        return BoldDilation.marginFor(this.base.getOversample(), this.strength());
    }

    @Override
    public void upload(int x, int y, GpuTexture texture) {
        NativeImage source = this.pixels.render();
        if (source == null) {
            // 让上层走降级（FontSet 会给回 missing 字形，BoldGlyphPair 会识破并退回双画）
            throw new IllegalStateException("bold dilation pixel source unavailable");
        }

        NativeImage dilated = null;

        try {
            dilated = BoldDilation.dilate(source, this.strength(), this.getOversample(), this.square);
            if (dilated == null) {
                throw new IllegalStateException("bold dilation unsupported for this glyph");
            }

            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, dilated, 0, 0, x, y);
        } finally {
            // 原版 TTF 的 upload 也是写完立刻 close —— writeToTexture 会把字节立刻交给后端
            closeQuietly(source);
            closeQuietly(dilated);
        }
    }

    private static void closeQuietly(NativeImage image) {
        if (image == null) {
            return;
        }

        try {
            image.close();
        } catch (Throwable ignored) {
            // 已经关过就算了
        }
    }
}
