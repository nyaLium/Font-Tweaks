package com.nyalium.fonttweaks;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.jspecify.annotations.Nullable;

/**
 * unihex 字体（原版 unifont，{@code UnihexProvider}）的像素来源。
 *
 * <p>这类字形没有图片文件，像素是运行时按十六进制位图算出来的：原版在自己的
 * {@code GlyphBitmap.upload} 里用 {@code UnihexProvider.unpackBitsToBytes} 把
 * {@code 16 行 × 字形宽度} 的位展开成 ARGB 像素，再直接写进纹理。</p>
 *
 * <p>这里做同一件事，只是先把像素落在 {@link NativeImage} 上、做膨胀、再上图集：
 * 调的还是原版那个 {@code unpackBitsToBytes}（私有静态，只能反射），所以位序、掩码、
 * 左右越界补白全都与原版逐像素一致 —— 不自己重写一遍位运算，就不会有「差一行一列」的偏差。</p>
 *
 * <p>原版这类字形 {@code isColored() == true}（整型 ARGB），所以这边产出 {@code RGBA} 位图，
 * 走彩色图集，与普通字形互不干扰。</p>
 *
 * <p>字段一律反射取（{@code contents} / {@code left} / {@code right} / {@code width()}）：
 * 字形是 record，直接 {@code @Shadow} 万一字段名或方法形状变了会连带 mixin 一起炸掉，
 * 反射失败只表现为「这一个字形退回原版」。</p>
 */
public final class UnihexPixelSource implements BoldPixelSource {
    /** 原版固定 16 行。 */
    private static final int HEIGHT = 16;

    private static Method unpack;
    private static boolean unpackResolved;

    private final Object glyph;
    private boolean resolved;
    private boolean available;
    private UnihexProvider.LineData contents;
    private int left;
    private int right;
    private int width;

    public UnihexPixelSource(Object glyph) {
        this.glyph = glyph;
    }

    @Override
    public @Nullable NativeImage render() {
        if (!this.resolve() || this.width <= 0) {
            return null;
        }

        Method method = unpack();
        if (method == null) {
            return null;
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, this.width, HEIGHT, true);

        try {
            IntBuffer pixels = IntBuffer.allocate(this.width * HEIGHT);
            method.invoke(null, pixels, this.contents, this.left, this.right);
            pixels.rewind();

            ByteBuffer buffer = image.getPixelBytes();
            for (int i = 0, count = this.width * HEIGHT; i < count; i++) {
                int argb = pixels.get(i);
                int offset = i * 4;
                buffer.put(offset, (byte) (argb >> 16));
                buffer.put(offset + 1, (byte) (argb >> 8));
                buffer.put(offset + 2, (byte) argb);
                buffer.put(offset + 3, (byte) (argb >>> 24));
            }

            return image;
        } catch (Throwable t) {
            image.close();
            return null;
        }
    }

    /** 反射读字形里的位图数据。 */
    private boolean resolve() {
        if (this.resolved) {
            return this.available;
        }

        this.resolved = true;

        try {
            Class<?> type = this.glyph.getClass();

            for (Field field : type.getDeclaredFields()) {
                String name = field.getName();
                if (!name.equals("contents") && !name.equals("left") && !name.equals("right")) {
                    continue;
                }

                field.setAccessible(true);

                switch (name) {
                    case "contents" -> this.contents = (UnihexProvider.LineData) field.get(this.glyph);
                    case "left" -> this.left = field.getInt(this.glyph);
                    case "right" -> this.right = field.getInt(this.glyph);
                    default -> {
                        // 不会到这里
                    }
                }
            }

            Method widthMethod = type.getMethod("width");
            // 字形类本身是包私有的，公开方法也得开可访问才行
            widthMethod.setAccessible(true);
            this.width = (Integer) widthMethod.invoke(this.glyph);
            this.available = this.contents != null && this.width > 0;
        } catch (Throwable t) {
            this.available = false;
        }

        return this.available;
    }

    /** 反射拿 {@code UnihexProvider.unpackBitsToBytes(IntBuffer, LineData, int, int)}。 */
    private static @Nullable Method unpack() {
        if (!unpackResolved) {
            unpackResolved = true;

            try {
                for (Method candidate : UnihexProvider.class.getDeclaredMethods()) {
                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (!candidate.getName().equals("unpackBitsToBytes") || parameters.length != 4) {
                        continue;
                    }

                    if (parameters[0] == IntBuffer.class && parameters[1] == UnihexProvider.LineData.class) {
                        candidate.setAccessible(true);
                        unpack = candidate;
                        break;
                    }
                }
            } catch (Throwable t) {
                unpack = null;
            }

        }

        return unpack;
    }
}
