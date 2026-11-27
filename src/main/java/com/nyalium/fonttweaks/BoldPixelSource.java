package com.nyalium.fonttweaks;

import com.mojang.blaze3d.platform.NativeImage;
import org.jspecify.annotations.Nullable;

/**
 * 「原字形像素」的来源抽象 —— 两种字体提供者拿像素的方式完全不同：
 *
 * <ul>
 *   <li>位图字体：像素常驻在一张大图里，抠出一块就行；</li>
 *   <li>TTF：没有常驻位图，要拿 FreeType 指针现场渲染一张。</li>
 * </ul>
 *
 * <p>统一成「给我一张严格等于字形尺寸的位图，用完我自己关」。</p>
 */
public interface BoldPixelSource {
    /**
     * 生成原字形位图（尺寸 = 字形格子的像素尺寸）。
     *
     * @return 新位图，<b>调用方负责 {@code close()}</b>；拿不到时返回 {@code null}（上层退回原版）
     */
    @Nullable NativeImage render();
}
