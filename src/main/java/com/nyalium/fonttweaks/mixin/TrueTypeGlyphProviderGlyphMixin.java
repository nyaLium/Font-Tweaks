package com.nyalium.fonttweaks.mixin;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.nyalium.fonttweaks.BoldDilatedBitmap;
import com.nyalium.fonttweaks.BoldGlyphFactory;
import com.nyalium.fonttweaks.BoldGlyphPair;
import com.nyalium.fonttweaks.BoldPixelSource;
import com.nyalium.fonttweaks.SignGlowOutline;
import com.nyalium.fonttweaks.TrueTypePixelSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 粗体渲染：把 TTF 字形（{@code TrueTypeGlyphProvider}）包成「普通 + 膨胀」二选一的一对。
 *
 * <p>为什么必须单独挂一个：资源包只要把 {@code assets/minecraft/font/default.json} 换成单个
 * {@code ttf} 提供者（「缝合像素 Fusion Pixel」「霞鹜文楷」这类中文像素 / 手写字体全是这种装法），
 * 整套 UI 的文字就<b>不再经过</b> {@code BitmapProvider} 了 —— 只挂位图那条路，
 * 粗体膨胀会一次都不生效。</p>
 *
 * <p>和位图版结构完全一致：{@code bake} 里那次 {@code stitch} 被接管，正常字形先照原样产出，
 * 再记下「怎么生成膨胀版」（{@link BoldGlyphFactory} + {@link TrueTypePixelSource}），
 * 真正膨胀延后到第一次画粗体时。{@code require = 0} 同样是兜底：注入不上就只是退回原版。</p>
 */
@Mixin(targets = "com.mojang.blaze3d.font.TrueTypeGlyphProvider$Glyph")
public abstract class TrueTypeGlyphProviderGlyphMixin {
    /** 原版 bake 里那次 stitch 的完整目标。 */
    private static final String STITCH =
            "Lcom/mojang/blaze3d/font/UnbakedGlyph$Stitcher;stitch("
                    + "Lcom/mojang/blaze3d/font/GlyphInfo;"
                    + "Lcom/mojang/blaze3d/font/GlyphBitmap;"
                    + ")Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;";

    @Shadow @Final private int width;
    @Shadow @Final private int height;
    @Shadow @Final private int index;

    @Redirect(method = "bake", at = @At(value = "INVOKE", target = STITCH), require = 0)
    private BakedGlyph cttHudFork$pairWithDilated(
            UnbakedGlyph.Stitcher stitcher, GlyphInfo info, GlyphBitmap bitmap) {
        BakedGlyph normal = stitcher.stitch(info, bitmap);
        if (normal == null) {
            return null;
        }

        BoldPixelSource pixels = new TrueTypePixelSource(this, this.width, this.height, this.index);
        return new BoldGlyphPair(
                normal,
                new BoldGlyphFactory(stitcher, info, bitmap, pixels),
                new BoldGlyphFactory(stitcher, info, bitmap, pixels, BoldDilatedBitmap.GLOW, true));
    }
}
