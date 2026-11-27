package com.nyalium.fonttweaks.mixin;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.nyalium.fonttweaks.BoldDilatedBitmap;
import com.nyalium.fonttweaks.BoldDilation;
import com.nyalium.fonttweaks.BoldGlyphFactory;
import com.nyalium.fonttweaks.BoldGlyphPair;
import com.nyalium.fonttweaks.BoldPixelSource;
import com.nyalium.fonttweaks.UnihexPixelSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 粗体渲染：把 unihex 字形（原版 unifont，中文等「字体包里没有的字」全靠它兜底）也包成一对。
 *
 * <p>这条最容易被忽略：资源包里的 TTF 一旦没有覆盖某个码位（或者干脆没开中文字体包），
 * 那个字就不是 {@code TrueTypeGlyphProvider} 出的，而是 {@code UnihexProvider} 出的。
 * 只挂另外两条时，同一行里会「英文变粗、中文不变」—— 不是中文特殊，而是字形根本不是
 * 同一个 provider 产的。</p>
 *
 * <p>结构与另外两个 provider 完全一致：{@code bake} 里那次 {@code stitch} 被接管，
 * 普通字形先照原样产出，再记下「怎么生成膨胀版」（{@link UnihexPixelSource} 按原版同一套
 * 位展开算法还原像素），真正膨胀延后到第一次画粗体时。</p>
 *
 * <p>字形是 record，字段名/方法形状随时可能变，所以这里<b>不 @Shadow</b> 任何成员，
 * 只取 {@code bake} 的入参；取字段交给反射，反射失败就只是这一个字形退回原版。</p>
 */
@Mixin(targets = "net.minecraft.client.gui.font.providers.UnihexProvider$Glyph")
public abstract class UnihexProviderGlyphMixin {
    /** 原版 bake 里那次 stitch 的完整目标。 */
    private static final String STITCH =
            "Lcom/mojang/blaze3d/font/UnbakedGlyph$Stitcher;stitch("
                    + "Lcom/mojang/blaze3d/font/GlyphInfo;"
                    + "Lcom/mojang/blaze3d/font/GlyphBitmap;"
                    + ")Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;";

    @Redirect(method = "bake", at = @At(value = "INVOKE", target = STITCH), require = 0)
    private BakedGlyph cttHudFork$pairWithDilated(
            UnbakedGlyph.Stitcher stitcher, GlyphInfo info, GlyphBitmap bitmap) {
        BakedGlyph normal = stitcher.stitch(info, bitmap);
        if (normal == null) {
            return null;
        }

        BoldPixelSource pixels = new UnihexPixelSource(this);
        return new BoldGlyphPair(
                normal,
                new BoldGlyphFactory(stitcher, info, bitmap, pixels),
                new BoldGlyphFactory(stitcher, info, bitmap, pixels, BoldDilatedBitmap.GLOW, true));
    }
}
