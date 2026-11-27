# Font Tweaks

[English](README.md) | **中文**

Minecraft Fabric 的纯客户端字体优化 mod。

- **粗体渲染优化**：原版粗体把同一个字形画两遍（第二遍右移 1 像素）。这里用掩膜膨胀把字形烘焙成一张更粗的贴图，
  粗体只画一遍（顶点数减半、四周对称加粗）；另有「偏移」模式，保留原版双画、只调第二遍的距离。
- **告示牌荧光描边优化**：原版发光文字要把整行字画 8 遍（±1 像素，3×3 去掉中心）来伪造描边。
  这里烘焙一张外扩 1 像素的膨胀字形，把这 8 遍合成 **1 遍** —— 观感不变，顶点数约降到 1/4.5。

## 下载

[GitHub Releases](https://github.com/nyaLium/Font-Tweaks/releases)

## 依赖

Minecraft 26.3、Fabric Loader ≥ 0.20.1、Fabric API、Java ≥ 25；Mod Menu 可选（只影响设置入口）。

## 配置

`config/font-tweaks.json`，或 Mod Menu → Font Tweaks：

| 项 | 默认 |
| --- | --- |
| 粗体渲染优化 / 粗体模式 | 开 / 膨胀 |
| 膨胀强度 / 偏移距离 | 0.25 / 0.35 |
| 告示牌荧光描边优化 / 描边距离上限 | 开 / 16 格 |
| 描边粗细 | 1.0 px（范围 0.5 – 1.0，步进 0.1） |

## 兼容

- 覆盖 `BitmapProvider`（原版位图字体）与 `TrueTypeGlyphProvider`（资源包里的 TTF）；
- 其它字形提供者、取不到像素、GPU 写入失败 → 自动退回原版，不会让文字消失；
- 与光影（Iris）、Sodium 无冲突（不替换字体渲染管线）；
- 如果有别的 mod 接管了字体渲染（Caxton、Ember's Text API），粗体优化会自动失效，荧光描边会自动退回原版 8 遍；

## 构建

```
gradle build --offline
```

## 许可

MIT（见 `LICENSE`）。
灵感来自 BoldTextFix（粗体多模式）与 FeyTweaks（荧光描边优化）的**思路**。
