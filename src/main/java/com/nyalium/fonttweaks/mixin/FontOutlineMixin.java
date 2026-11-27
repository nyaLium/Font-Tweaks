package com.nyalium.fonttweaks.mixin;

import com.nyalium.fonttweaks.SignGlowOutline;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 告示牌荧光文本：把原版的 <b>8 遍描边</b>换成 <b>1 遍</b>。
 *
 * <p>原版 {@code Font.prepare8xTextOutline} 会把整行字按 ±1 像素的 3×3 去掉中心画 8 遍来
 * 伪造外发光。这里在 HEAD 拦下来，改用 {@link SignGlowOutline} 生成的单遍文本 ——
 * 每个字形换成「外扩 1 像素」的描边贴图，一遍就覆盖原来 8 个方向的结果。</p>
 *
 * <p>只影响「带描边色」的文本（原版世界里只有发光告示牌走这条路），且
 * {@code SignGlowOutline.build} 只要发现有一个字形不支持就会返回 {@code null}，
 * 原版逻辑照常执行 —— 属于「全有或全无」的降级。</p>
 */
@Mixin(Font.class)
public abstract class FontOutlineMixin {

    @Inject(method = "prepare8xTextOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void cttHudFork$singlePassOutline(FormattedCharSequence text, float x, float y, int outlineColor,
                                              CallbackInfoReturnable<Font.PreparedText> cir) {
        if (!SignGlowOutline.enabled()) {
            return;
        }

        Font.PreparedText fast = SignGlowOutline.build((Font) (Object) this, text, x, y, outlineColor);
        if (fast != null) {
            cir.setReturnValue(fast);
        }
    }
}
