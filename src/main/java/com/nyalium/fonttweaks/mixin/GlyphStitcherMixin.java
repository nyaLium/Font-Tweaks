package com.nyalium.fonttweaks.mixin;

import com.nyalium.fonttweaks.BoldDilation;
import com.nyalium.fonttweaks.SignGlowOutline;
import net.minecraft.client.gui.font.GlyphStitcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 粗体膨胀的清理点。
 *
 * <p>{@code FontSet.resetTextures()} 会调 {@code GlyphStitcher.reset()} 释放整张字形图集
 * （资源包 / 语言 / 字体选项变化时都会走这里）。这一刻之前分出去的槽位全部作废，
 * 所以同一时刻：释放膨胀过程中暂存的位图，并让 {@link BoldDilation#generation()} 自增，
 * 使已经建好的「配对字形」不再往旧 stitcher 上图集。</p>
 */
@Mixin(GlyphStitcher.class)
public abstract class GlyphStitcherMixin {

    @Inject(method = "reset", at = @At("HEAD"), require = 0)
    private void cttHudFork$dropBoldDilation(CallbackInfo ci) {
        BoldDilation.clear();
        SignGlowOutline.reset();
    }
}
