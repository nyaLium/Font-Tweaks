package com.nyalium.fonttweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 告示牌荧光文本（发光字）的描边优化。
 *
 * <h2>原版开销</h2>
 * <p>发光字的「外发光」是 {@code Font.prepare8xTextOutline} —— 把整行字按 ±1 像素的 3×3
 * 去掉中心<b>画 8 遍</b>，正文再画 1 遍。一行 15 个字就是 120 个描边四边形，而且可见告示牌
 * 每帧都会重新准备一次文本。</p>
 *
 * <h2>做法</h2>
 * <p>把字形位图按 <b>1 像素</b>做掩膜膨胀（外扩一圈），生成一张「描边字形」贴图，
 * 于是 8 遍可以合成 <b>1 遍</b>：</p>
 * <pre>
 * 原版：8 遍描边（NORMAL）+ 1 遍正文（POLYGON_OFFSET）  → 9 × 字数 个四边形
 * 优化：1 遍描边（NORMAL）+ 1 遍正文（POLYGON_OFFSET）  → 2 × 字数 个四边形
 * </pre>
 * <p>渲染顺序、display mode、颜色（描边用 {@code darkColor}、正文用字色）都与原版一致，
 * 所以观感基本不变，只是顶点数降到约 1/。</p>
 *
 * <h2>怎么接进去</h2>
 * <p>不自己造 {@code PreparedText}，而是复用原版 {@code Font.prepareText}：在
 * {@link #build} 里置一个线程内标记，再让 {@link BoldGlyphPair#createGlyph} 看到标记时
 * 改用「描边变体」。这样排版（advance / 换行 / 空格）与原版逐字一致，也少一份维护成本。</p>
 *
 * <h2>降级</h2>
 * <p>只要有<b>一个</b>字形拿不到描边变体（非位图 / 非 TTF 字体、纹理分配失败…），
 * {@link #build} 就返回 {@code null}，调用方会原样跑回原版 8 遍描边 —— 绝不出现
 * 「描边突然变细」的半吊子效果。任何异常同样整体停用。</p>
 */
public final class SignGlowOutline {
    private static final Logger LOGGER = LoggerFactory.getLogger("font-tweaks/sign");

    /** 默认描边粗细：1 像素 = 原版 8 遍描边的外扩量。 */
    public static final double DEFAULT_WIDTH = 1.0;

    /** 当前配置的描边粗细（0.5 ~ 1.0 GUI 像素）。 */
    public static float width() {
        Config.Cfg cfg = Config.get();
        double v = cfg == null ? DEFAULT_WIDTH : cfg.signGlowWidth;
        return (float) Math.max(0.5, Math.min(1.0, v));
    }
    /** 原版自己的描边距离上限（格）。 */
    public static final int VANILLA_DISTANCE = 16;
    /** 连续这么多次「有字形没有描边变体」就整体停用。 */
    private static final int MISS_LIMIT = 8;

    /** 「正在生成描边文本」标记：只在客户端渲染线程里为 true。 */
    private static boolean outlineRequest;
    /** 本次生成里有没有字形拿不到描边变体（有就整体退回原版）。 */
    private static boolean missed;
    private static int missCount;
    private static boolean announced;
    private static boolean failed;

    private SignGlowOutline() {
    }

    /** 总开关：{@code 杂项 → 告示牌荧光描边优化}。 */
    public static boolean enabled() {
        if (failed) {
            return false;
        }

        Config.Cfg cfg = Config.get();
        return cfg != null && cfg.signGlowOutline;
    }

    /** {@link BoldGlyphPair} 用它判断「这次是在画描边」。 */
    static boolean isOutlineRequest() {
        return outlineRequest;
    }

    /** 某个字形没有描边变体 → 本次整体退回原版。 */
    static void noteMiss() {
        missed = true;

        // 偶尔一次（比如刚好撞上字体图集重载）不算问题；连着多次就当这条路的字形支持不了，
        // 干脆整体关掉，免得每帧都白准备一遍再丢掉。
        if (++missCount >= MISS_LIMIT && !failed) {
            failed = true;
            LOGGER.warn("[Font Tweaks] 告示牌荧光描边优化连续 {} 次失败，已停用（回到原版 8 遍描边）", MISS_LIMIT);
        }
    }

    /**
     * 字体图集重载后调用：清掉上一轮的失败计数与「永久停用」标记。
     *
     * <p>不重置的话，重载几次（资源包 / 字体包切换）累积到 {@link #MISS_LIMIT} 就会把优化
     * 永久关掉 —— 表现为「换完资源包，荧光描边优化失效」。</p>
     */
    public static void reset() {
        failed = false;
        missed = false;
        missCount = 0;
        announced = false;
    }

    /** 第一个描边字形真的上图集了 —— 往日志写一行（排查装配用）。 */
    static void noteApplied() {
        if (!announced) {
            announced = true;
            LOGGER.info("[Font Tweaks] 告示牌荧光描边优化已生效（8 遍描边 → 1 遍）");
        }
    }

    /**
     * 生成「单遍描边」文本。
     *
     * @return {@code null} = 有字形支持不了 / 出错，调用方应退回原版 8 遍描边
     */
    public static Font.@Nullable PreparedText build(
        Font font, FormattedCharSequence text, float x, float y, int outlineColor
    ) {
        outlineRequest = true;
        missed = false;

        try {
            // 按原版的做法把每个字形的颜色替换成描边色：告示牌文本的 style 里可能自带颜色
            // （命令 / NBT 写的彩色告示牌），不换的话描边会跟着正文颜色走。
            FormattedCharSequence recolored = sink -> text.accept(
                (index, style, codepoint) -> sink.accept(index, style.withColor(outlineColor), codepoint)
            );

            Font.PreparedText prepared = font.prepareText(recolored, x, y, outlineColor, false, false, 0);
            if (missed) {
                return null;
            }

            missCount = 0;   // 这一轮全部成功 → 清掉失败计数
            return prepared;
        } catch (Throwable t) {
            failed = true;
            LOGGER.warn("[Font Tweaks] 告示牌荧光描边优化已停用，退回原版 8 遍描边", t);
            return null;
        } finally {
            outlineRequest = false;
        }
    }

    /**
     * 距离收窄：{@code false} = 这一块告示牌不画描边。
     *
     * <p>原版只在 16 格内画描边；这里允许再往小调（大厅 / 指路牌区很密时有用）。
     * 望远镜（第一人称瞄准）时保持原版行为，不受距离限制。</p>
     */
    public static boolean allowOutline(BlockPos pos) {
        Config.Cfg cfg = Config.get();
        if (cfg == null || !cfg.signGlowOutline) {
            return true;
        }

        int limit = cfg.signGlowDistance;
        if (limit >= VANILLA_DISTANCE) {
            return true;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
            && minecraft.options.getCameraType().isFirstPerson()
            && minecraft.player.isScoping()) {
            return true;
        }

        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return true;
        }

        return camera.distanceToSqr(Vec3.atCenterOf(pos)) <= (double) limit * limit;
    }
}
