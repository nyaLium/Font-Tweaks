package com.nyalium.fonttweaks.mixin;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.nyalium.fonttweaks.BitmapPixelSource;
import com.nyalium.fonttweaks.BoldDilatedBitmap;
import com.nyalium.fonttweaks.BoldGlyphFactory;
import com.nyalium.fonttweaks.BoldGlyphPair;
import com.nyalium.fonttweaks.BoldPixelSource;
import com.nyalium.fonttweaks.SignGlowOutline;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 粗体渲染：把 {@code BitmapProvider} 的每个字形包成「普通 + 膨胀」二选一的一对。
 *
 * <p>挂的是 {@code BitmapProvider$Glyph#bake(Stitcher)} 里唯一那次
 * {@code stitcher.stitch(info, bitmap)}：原样调用一次拿到普通字形，再用
 * {@link BoldGlyphFactory} 记下「怎么生成膨胀版」，最后返回
 * {@link BoldGlyphPair}。膨胀版本身<b>此刻不生成</b>，等真的画到粗体时才做，
 * 所以没有粗体的界面（几乎全部界面）零额外开销。</p>
 *
 * <p>这是原版唯一真正在用、且带有可读像素的字形提供者（ascii / accented /
 * nonlatin_european 都是 bitmap）。其它提供者（unihex / TTF / 特殊字形）连这个
 * 方法都不会进，自然退回原版双画。</p>
 *
 * <p>{@code require = 0}：这是一个 record 内部类的注入点，万一哪天原版重构让它对不上，
 * 结果应该是「粗体退回原版」而不是「游戏起不来」。真生效时日志会有一行
 * {@code [Font Tweaks] 粗体膨胀已生效}。</p>
 */
@Mixin(targets = "net.minecraft.client.gui.font.providers.BitmapProvider$Glyph")
public abstract class BitmapProviderGlyphMixin {
    /** 原版 bake 里那次 stitch 的完整目标。 */
    private static final String STITCH =
            "Lcom/mojang/blaze3d/font/UnbakedGlyph$Stitcher;stitch("
                    + "Lcom/mojang/blaze3d/font/GlyphInfo;"
                    + "Lcom/mojang/blaze3d/font/GlyphBitmap;"
                    + ")Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;";

    @Shadow @Final private int offsetX;
    @Shadow @Final private int offsetY;
    @Shadow @Final private int width;
    @Shadow @Final private int height;

    @Redirect(method = "bake", at = @At(value = "INVOKE", target = STITCH), require = 0)
    private BakedGlyph cttHudFork$pairWithDilated(
            UnbakedGlyph.Stitcher stitcher, GlyphInfo info, GlyphBitmap bitmap) {
        BakedGlyph normal = stitcher.stitch(info, bitmap);
        if (normal == null) {
            return null;
        }

        BoldPixelSource pixels =
                new BitmapPixelSource(this, this.offsetX, this.offsetY, this.width, this.height);
        return new BoldGlyphPair(
                normal,
                new BoldGlyphFactory(stitcher, info, bitmap, pixels),
                new BoldGlyphFactory(stitcher, info, bitmap, pixels, BoldDilatedBitmap.GLOW, true));
    }
}
