package com.nyalium.fonttweaks.mixin;

import com.nyalium.fonttweaks.SignGlowOutline;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 告示牌荧光描边：距离上限可调（只往小收，不放大）。
 *
 * <p>原版 {@code AbstractSignRenderer.isOutlineVisible} 固定 16 格内才画描边（望远镜瞄准时例外）。
 * 这里在它返回 {@code true} 之后再看一眼配置：{@code 杂项 → 描边距离上限} 改小到 N 格时，
 * 超过 N 格的告示牌不再画描边 —— 大厅、指路牌区这种「满屏发光牌子」的场景能再省一大截。</p>
 *
 * <p>只做「收窄」而不做「放大」：上限 ≥ 16 时直接放行原版结果，所以默认配置下行为与
 * 原版完全一致。望远镜（第一人称瞄准）时同样不干预。</p>
 */
@Mixin(AbstractSignRenderer.class)
public abstract class SignOutlineDistanceMixin {

    @Inject(method = "isOutlineVisible", at = @At("RETURN"), cancellable = true, require = 0)
    private static void cttHudFork$limitOutlineDistance(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }

        if (!SignGlowOutline.allowOutline(pos)) {
            cir.setReturnValue(false);
        }
    }
}
