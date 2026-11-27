package com.nyalium.fonttweaks;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import org.lwjgl.util.freetype.FT_Face;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 粗体「字形膨胀」（mask dilation）引擎 —— 「杂项 → 粗体渲染」两种模式的底层实现。
 *
 * <h2>为什么需要它</h2>
 * <p>26.3 的原版粗体并不是换字体，而是把<b>同一个字形画两遍</b>：第二遍右移
 * {@code GlyphInfo.getBoldOffset()}（默认 1 像素），外加四边形每边再胀 0.1 像素
 * （{@code BakedSheetGlyph.extraThickness}）。代价是<b>顶点数 / 绘制次数翻倍</b>。</p>
 *
 * <h2>两种模式</h2>
 * <ul>
 *   <li><b>膨胀（{@link #MODE_DILATION}，默认）</b>：把字形位图按「标称像素」做掩膜膨胀，
 *       生成一张更粗的独立字形贴图，粗体只画<b>一遍</b>。顶点数减半，观感接近原版粗体
 *       但更干净（四周对称加粗，而不是整体右移）。</li>
 *   <li><b>偏移（{@link #MODE_OFFSET}）</b>：保留原版双画，只把第二遍的平移距离换成可调值。</li>
 * </ul>
 *
 * <h2>作用范围</h2>
 * <p>覆盖两条真实的字形来源：</p>
 * <ul>
 *   <li>{@code BitmapProvider}（原版 ascii / accented / nonlatin_european 等位图字体）；</li>
 *   <li>{@code TrueTypeGlyphProvider}（资源包里的 TTF，例如「缝合像素 Fusion Pixel」——
 *       它会把 default.json 整个换成单个 ttf 提供者，这也是最常见的「中文像素字体」装法）。</li>
 * </ul>
 * <p>其余（unihex / 特殊字形 / 无 alpha 通道的位图）自动退回原版双画。任何一步出错都会
 * 在日志里留一条警告并<b>整体关掉膨胀</b>，绝不会让文字消失。</p>
 *
 * <h2>膨胀半径的单位</h2>
 * <p>强度的单位是<b>GUI 像素</b>（和滑块一致），但位图是「纹素」：TTF 字形的
 * {@code oversample} 通常是 3，1 纹素只有 1/3 GUI 像素。所以半径要按
 * {@code strength × oversample} 换算成纹素，边距也要相应放大 —— 否则在 TTF 字体上
 * 0.5 像素的膨胀会退化到肉眼看不见的 0.17 像素。</p>
 */
public final class BoldDilation {
    private static final Logger LOGGER = LoggerFactory.getLogger("font-tweaks/bold");

    /** 粗体模式：膨胀（省一半顶点）。 */
    public static final int MODE_DILATION = 0;
    /** 粗体模式：保留双画，只调第二遍的偏移距离。 */
    public static final int MODE_OFFSET = 1;

    /** 单个字形格子的纹素上限：超过就不膨胀（畸形资源包保护）。 */
    private static final int MAX_CELL_PIXELS = 96;
    /** 边距上限（纹素）：oversample 再大也不至于扩出去太多。 */
    private static final int MAX_MARGIN = 8;

    /** 字体图集代次：重载后旧的「配对字形」靠它作废。 */
    private static int generation;

    /** 出过错就永久降级到原版双画。 */
    private static boolean failed;

    /** 是否已经往日志里报过一次「膨胀真的生效了」。 */
    private static boolean announced;

    /** 上一次看到的膨胀强度 / 参数代次：强度一改，已烘焙的膨胀字形就得重做。 */
    private static float lastStrength = -1.0F;
    private static int paramsVersion;

    private static Field bitmapHolderField;
    private static Field bitmapImageField;
    private static Field trueTypeOuterField;
    private static Field trueTypeFaceField;
    private static Field trueTypeMemoryField;

    private BoldDilation() {
    }

    /** 当前图集代次。 */
    public static int generation() {
        return generation;
    }

    /** 总开关：{@code 杂项 → 粗体渲染 → 是否启用}。 */
    public static boolean enabled() {
        if (failed) {
            return false;
        }

        Config.Cfg cfg = Config.get();
        return cfg != null && cfg.boldOptimize;
    }

    /** 当前是否是「膨胀」模式。 */
    public static boolean dilationMode() {
        Config.Cfg cfg = Config.get();
        return cfg == null || cfg.boldMode == MODE_DILATION;
    }

    /** 膨胀强度（0 ~ 0.5，单位 GUI 像素）。 */
    public static float strength() {
        Config.Cfg cfg = Config.get();
        double value = cfg == null ? 0.25 : cfg.boldDilation;
        return (float) clamp(value, 0.0, 0.5);
    }

    /** 偏移模式下第二遍平移的距离（0 ~ 1.0，单位 GUI 像素）。 */
    public static float offset() {
        Config.Cfg cfg = Config.get();
        double value = cfg == null ? 0.35 : cfg.boldOffset;
        return (float) clamp(value, 0.0, 1.0);
    }

    /**
     * 膨胀参数代次 —— 强度一变就自增。
     *
     * <p>膨胀结果会进字形图集，改强度不会自动重画。这里用「代次」做惰性重做：
     * {@link BoldGlyphPair} 发现代次变了就重新膨胀一次，于是拖动滑块后<b>接下来画到的</b>
     * 粗体字立刻用新强度（旧的图集槽位留到下次字体重载时回收）。</p>
     */
    public static int paramsVersion() {
        float strength = strength();
        if (strength != lastStrength) {
            lastStrength = strength;
            paramsVersion++;
        }

        return paramsVersion;
    }

    /**
     * 膨胀需要往外扩多少<b>纹素</b>。
     *
     * @param oversample 一个 GUI 像素对应多少纹素（位图字体是 1，TTF 常见 3）
     * @param strength   膨胀强度（GUI 像素）
     */
    public static int marginFor(float oversample, float strength) {
        float texels = Math.max(0.0F, strength) * Math.max(1.0F, oversample);
        int margin = (int) Math.ceil(texels - 1.0E-4F);
        return Math.max(1, Math.min(MAX_MARGIN, margin));
    }

    /** 解除「永久停用」状态（用户在设置里切换开关时调用，让功能有机会重试）。 */
    public static void reset() {
        failed = false;
        oobReported = false;
    }

    static void disable(String reason, Throwable cause) {
        if (!failed) {
            failed = true;
            LOGGER.warn("[Font Tweaks] 粗体膨胀已停用（{}），粗体回到原版双画", reason, cause);
        }
    }

    /**
     * 字体图集重载：让所有还活着的「配对字形」作废。
     *
     * <p>由 {@code GlyphStitcher.reset()} 触发 —— 那一刻旧的槽位全部失效，
     * 之前的膨胀贴图也不再有意义。</p>
     */
    public static void clear() {
        generation++;
        failed = false;
    }

    /**
     * 第一个膨胀字形真的上图集了 —— 往日志写一行。
     *
     * <p>「装配是否生效」只看这一行：看到它说明 mixin 挂上了、像素读到了、GPU 写入也过了；
     * 没看到（又确实有粗体字）说明某一步退回原版了，同名类名下的 WARN 会说明原因。</p>
     */
    static void noteFirstGlyph() {
        if (!announced) {
            announced = true;
            LOGGER.info("[Font Tweaks] 粗体膨胀已生效（强度 {} 像素）", strength());
        }
    }

    /**
     * 按纹素半径做掩膜膨胀，输出一张四周各留 {@link #marginFor} 纹素的新图。
     *
     * <p>模型：每个源像素的覆盖度向外“渗出”{@code radius} 个纹素，距离 {@code d} 处的
     * 邻居得到 {@code clamp(radius - d + 1, 0, 1)} 的权重（像素中心模型）。所以
     * <ul>
     *   <li>{@code radius = 0.5}（位图字体 + 0.5 像素）：相邻纹素得到 50% 覆盖 —— 半个像素的加粗；</li>
     *   <li>{@code radius = 1.5}（TTF oversample=3 + 0.5 像素）：1 纹素外满覆盖、2 纹素外 50%。</li>
     * </ul></p>
     *
     * <p>支持 {@code RGBA}（彩色 / 带调色板解出来的位图）与 {@code LUMINANCE}（FreeType
     * 生成的字形）；其它格式返回 {@code null} 交给上层退回原版。</p>
     */
    public static NativeImage dilate(NativeImage source, float strength, float oversample) {
        return dilate(source, strength, oversample, false);
    }

    /**
     * @param square {@code true} = 用<b>方形</b>（切比雪夫距离）而不是圆盘：3×3 邻域内一律满覆盖。
     *               告示牌荧光描边要的正是这个 —— 原版 8 遍描边就是 ±1 像素的 8 个方向各画一遍，
     *               用圆盘会让四个斜角只有 59% 覆盖，明显比原版淡。
     */
    public static NativeImage dilate(NativeImage source, float strength, float oversample, boolean square) {
        if (source == null || source.isClosed()) {
            return null;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0 || width > MAX_CELL_PIXELS || height > MAX_CELL_PIXELS) {
            logOnce("oversize", "膨胀跳过：尺寸 {}x{} 超出上限 {}", width, height, MAX_CELL_PIXELS);
            return null;
        }

        NativeImage.Format format = source.format();
        int components = format.components();
        boolean rgba = components == 4;
        boolean single = components == 1;
        if (!format.hasLuminanceOrAlpha() || (!rgba && !single)) {
            logOnce("unsupported-format", "膨胀跳过：format={} {}x{}", format, width, height);
            return null;
        }

        int margin = marginFor(oversample, strength);
        int outWidth = width + 2 * margin;
        int outHeight = height + 2 * margin;
        float radius = Math.max(0.0F, strength) * Math.max(1.0F, oversample);
        int squareRadius = (int) Math.floor(radius + 1.0E-4F);

        NativeImage out;
        try {
            out = new NativeImage(format, outWidth, outHeight, true);
        } catch (Throwable t) {
            disable("分配膨胀位图失败", t);
            return null;
        }

        try {
            ByteBuffer src = source.getPixelBytes();
            ByteBuffer dst = out.getPixelBytes();
            if (!diagDone) {
                diagDone = true;
                LOGGER.info("[Font Tweaks] dilate diag: format={} {}x{} comps={} coverOff={} srcLimit={} dstLimit={} margin={}",
                        format, width, height, components, format.luminanceOrAlphaOffset() / 8,
                        src.limit(), dst.limit(), margin);
            }
            // 注意：Format.luminanceOrAlphaOffset() 返回的是**位**偏移（RGBA 的 alpha 在 24 位），
            // 而 getPixelBytes() 是按字节索引的缓冲区，所以必须 /8 换成字节偏移（RGBA → 3）。
            int coverageOffset = format.luminanceOrAlphaOffset() / 8;
            int stride = width * components;
            int outStride = outWidth * components;

            for (int y = 0; y < outHeight; y++) {
                for (int x = 0; x < outWidth; x++) {
                    int sx = x - margin;
                    int sy = y - margin;
                    int outCoverage = sample(src, stride, components, coverageOffset, width, height, sx, sy);
                    int pixel = (sx >= 0 && sy >= 0 && sx < width && sy < height)
                        ? sy * stride + sx * components
                        : -1;

                    for (int dy = -margin; dy <= margin; dy++) {
                        for (int dx = -margin; dx <= margin; dx++) {
                            if (dx == 0 && dy == 0) {
                                continue;
                            }

                            float weight;
                            if (square) {
                                if (Math.max(Math.abs(dx), Math.abs(dy)) > squareRadius) {
                                    continue;
                                }

                                weight = 1.0F;
                            } else {
                                weight = radius - (float) Math.sqrt(dx * dx + dy * dy) + 1.0F;
                                if (weight <= 0.0F) {
                                    continue;
                                }

                                weight = Math.min(1.0F, weight);
                            }

                            int neighbour = sample(
                                src, stride, components, coverageOffset, width, height, sx + dx, sy + dy
                            );
                            if (neighbour <= 0) {
                                continue;
                            }

                            int score = Math.round(neighbour * Math.min(1.0F, weight));
                            if (score > outCoverage) {
                                outCoverage = score;
                                pixel = (sy + dy) * stride + (sx + dx) * components;
                            }
                        }
                    }

                    if (outCoverage <= 0) {
                        continue;
                    }

                    int outIndex = y * outStride + x * components;
                    if (pixel >= 0) {
                        for (int c = 0; c < components; c++) {
                            if (rgba && c == coverageOffset) {
                                continue;
                            }

                            dst.put(outIndex + c, src.get(pixel + c));
                        }
                    }

                    dst.put(outIndex + coverageOffset, (byte) Math.min(255, outCoverage));
                }
            }

            return out;
        } catch (Throwable t) {
            out.close();
            disable("膨胀计算失败", t);
            return null;
        }
    }

    /**
     * 从一张大位图里裁出 {@code (x, y)} 起 {@code width × height} 的字形格子。
     *
     * <p>按<b>原始字节</b>逐行拷贝，不依赖像素格式（RGBA / LUMINANCE / 未来新增的都行）。</p>
     */
    public static NativeImage crop(NativeImage source, int x, int y, int width, int height) {
        if (source == null || source.isClosed() || width <= 0 || height <= 0) {
            return null;
        }

        if (x < 0 || y < 0 || x + width > source.getWidth() || y + height > source.getHeight()) {
            return null;
        }

        NativeImage.Format format = source.format();
        int components = format.components();
        int sourceStride = source.getWidth() * components;
        int rowBytes = width * components;

        NativeImage cell;
        try {
            cell = new NativeImage(format, width, height, true);
        } catch (Throwable t) {
            disable("分配字形格子失败", t);
            return null;
        }

        try {
            ByteBuffer src = source.getPixelBytes();
            ByteBuffer dst = cell.getPixelBytes();
            for (int row = 0; row < height; row++) {
                int from = (y + row) * sourceStride + x * components;
                int to = row * rowBytes;
                for (int i = 0; i < rowBytes; i++) {
                    dst.put(to + i, src.get(from + i));
                }
            }

            return cell;
        } catch (Throwable t) {
            cell.close();
            disable("裁剪字形失败", t);
            return null;
        }
    }

    /**
     * 结构反射：从 {@code BitmapProvider.Glyph} 里取出它持有的整张字形位图。
     *
     * <p>那个 record 的字段类型是私有的 {@code ImageDataHolder}，源码里写不出这个名字，
     * 生产环境字段名也可能被改。所以按<b>结构</b>找：record 里唯一的非原始类型字段就是
     * {@code imageData}；holder 里唯一的 {@link NativeImage} 字段就是位图本身。</p>
     */
    public static NativeImage imageOf(Object glyph) {
        if (glyph == null) {
            return null;
        }

        try {
            if (bitmapHolderField == null) {
                for (Field field : glyph.getClass().getDeclaredFields()) {
                    if (!field.isSynthetic() && !field.getType().isPrimitive()) {
                        field.setAccessible(true);
                        bitmapHolderField = field;
                        break;
                    }
                }
            }

            if (bitmapHolderField == null) {
                return null;
            }

            Object holder = bitmapHolderField.get(glyph);
            if (holder == null) {
                return null;
            }

            if (bitmapImageField == null) {
                for (Field field : holder.getClass().getDeclaredFields()) {
                    if (field.getType() == NativeImage.class) {
                        field.setAccessible(true);
                        bitmapImageField = field;
                        break;
                    }
                }
            }

            if (bitmapImageField == null) {
                return null;
            }

            NativeImage image = (NativeImage) bitmapImageField.get(holder);
            return image == null || image.isClosed() ? null : image;
        } catch (Throwable t) {
            disable("取不到字形位图", t);
            return null;
        }
    }

    /**
     * 结构反射：从 {@code TrueTypeGlyphProvider.Glyph} 拿到 FreeType 的字体指针。
     *
     * <p>TTF 字形没有常驻位图 —— 像素是 {@code NativeImage.copyFromFont(face, index)} 现生成的。
     * 所以要拿到外围的 {@code TrueTypeGlyphProvider}，再取它的 {@code FT_Face}。同样按类型找，
     * 不依赖字段名：字形里唯一的 {@link TrueTypeGlyphProvider} 字段就是 {@code this$0}；
     * provider 里唯一的 {@link FT_Face} / {@link ByteBuffer} 就是字体指针与字体内存。</p>
     *
     * @return 字体指针；provider 已关闭或取不到时返回 {@code null}
     */
    public static FT_Face trueTypeFace(Object glyph) {
        if (glyph == null) {
            return null;
        }

        try {
            if (trueTypeOuterField == null) {
                for (Field field : glyph.getClass().getDeclaredFields()) {
                    if (field.getType() == TrueTypeGlyphProvider.class) {
                        field.setAccessible(true);
                        trueTypeOuterField = field;
                        break;
                    }
                }
            }

            if (trueTypeOuterField == null) {
                return null;
            }

            Object provider = trueTypeOuterField.get(glyph);
            if (provider == null) {
                return null;
            }

            if (trueTypeFaceField == null || trueTypeMemoryField == null) {
                for (Field field : provider.getClass().getDeclaredFields()) {
                    Class<?> type = field.getType();
                    if (type == FT_Face.class) {
                        field.setAccessible(true);
                        trueTypeFaceField = field;
                    } else if (type == ByteBuffer.class) {
                        field.setAccessible(true);
                        trueTypeMemoryField = field;
                    }
                }
            }

            if (trueTypeFaceField == null) {
                return null;
            }

            // provider 关闭后会先释放 fontMemory 再置空 face，这里用它当「还活着」的判据
            if (trueTypeMemoryField != null && trueTypeMemoryField.get(provider) == null) {
                return null;
            }

            return (FT_Face) trueTypeFaceField.get(provider);
        } catch (Throwable t) {
            disable("取不到 TTF 字体指针", t);
            return null;
        }
    }

    private static int sample(
        ByteBuffer bytes, int stride, int components, int coverageOffset, int width, int height, int x, int y
    ) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }

        int index = y * stride + x * components + coverageOffset;
        if (index < 0 || index >= bytes.limit()) {
            if (!oobReported) {
                oobReported = true;
                LOGGER.warn("[Font Tweaks] 字形缓冲区越界：index={} limit={} ({}x{}, {} comps, stride {})",
                        index, bytes.limit(), width, height, components, stride);
            }
            // 越界说明「图像尺寸」与「缓冲区大小」对不上：记一行诊断后中止，
            // 由上层 disable() 退回原版（宁可没有优化，也不能让文字变成空白）。
            return 0;
        }

        return bytes.get(index) & 0xFF;
    }

    private static boolean oobReported;

    /** 每种「跳过原因」只记一行，避免刷屏。 */
    static void logOnce(String key, String format, Object... args) {
        if (!LOGGED.add(key)) {
            return;
        }

        LOGGER.warn("[Font Tweaks] " + format, args);
    }

    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean diagDone;

    private static double clamp(double value, double lo, double hi) {
        return value < lo ? lo : (value > hi ? hi : value);
    }
}
