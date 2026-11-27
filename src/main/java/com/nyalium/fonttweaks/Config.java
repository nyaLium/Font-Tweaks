package com.nyalium.fonttweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Font Tweaks 的配置（{@code config/font-tweaks.json}）。
 *
 * <p>字段与 CTT HUD - Fork 里的同名设置一一对应，方便以后做一次性迁移。</p>
 */
public final class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("font-tweaks");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "font-tweaks.json");

    public static final int MODE_DILATION = 0;
    public static final int MODE_OFFSET = 1;

    public static final class Cfg {
        /** 粗体渲染优化总开关（默认开）。 */
        public boolean boldOptimize = true;
        /** 粗体模式：0 = 膨胀（默认），1 = 偏移。 */
        public int boldMode = MODE_DILATION;
        /** 膨胀强度（0 ~ 0.5 GUI 像素，默认 0.25）。 */
        public double boldDilation = 0.25;
        /** 偏移距离（0 ~ 1.0 GUI 像素，默认 0.35）。 */
        public double boldOffset = 0.35;
        /** 荧光文本描边优化（8 遍 → 1 遍，默认开）。 */
        public boolean signGlowOutline = true;
        /** 描边距离上限（4 ~ 16 格，默认 16 = 原版）。 */
        public int signGlowDistance = 16;
        /** 描边粗细（0.5 ~ 1.0 GUI 像素，默认 1.0 = 原版）。 */
        public double signGlowWidth = 1.0;
    }

    private static Cfg cfg = new Cfg();
    private static boolean loaded;

    private Config() {
    }

    public static Cfg get() {
        if (!loaded) load();
        return cfg;
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(cfg), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[Font Tweaks] 保存配置失败", e);
        }
    }

    private static void load() {
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                Cfg read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Cfg.class);
                if (read != null) {
                    cfg = normalize(read);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[Font Tweaks] 读取配置失败，使用默认值", t);
            cfg = new Cfg();
        }
    }

    public static void resetBold() {
        Cfg d = new Cfg();
        cfg.boldOptimize = d.boldOptimize;
        cfg.boldMode = d.boldMode;
        cfg.boldDilation = d.boldDilation;
        cfg.boldOffset = d.boldOffset;
    }

    public static void resetGlow() {
        Cfg d = new Cfg();
        cfg.signGlowOutline = d.signGlowOutline;
        cfg.signGlowDistance = d.signGlowDistance;
        cfg.signGlowWidth = d.signGlowWidth;
    }

    private static Cfg normalize(Cfg read) {
        read.boldMode = (int) clamp(read.boldMode, 0, 1);
        read.boldDilation = round2(clamp(read.boldDilation, 0.0, 0.5));
        read.boldOffset = round2(clamp(read.boldOffset, 0.0, 1.0));
        read.signGlowDistance = (int) clamp(read.signGlowDistance, 4, 16);
        read.signGlowWidth = round1(clamp(read.signGlowWidth, 0.5, 1.0));
        return read;
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return hi;
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
