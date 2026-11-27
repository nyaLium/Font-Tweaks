# Font Tweaks

**English** | [中文](README.zh-CN.md)

A client-side font rendering optimization mod for **Minecraft / Fabric**.

- **Bold rendering optimization** — vanilla draws every bold glyph twice (second copy shifted right). This mod
  mask-dilates the glyph bitmap into a thicker atlas entry, so bold text is drawn **once** (half the quads,
  symmetric weight). An "offset" mode is also available: keep the vanilla double draw and only tune its distance.
- **Glowing sign outline optimization** — vanilla draws glowing text **8 times** (±1 px, 3×3 minus center) to fake
  an outline. This mod bakes a 1 px dilated glyph and merges those 8 passes into **1** — same look, roughly 1/4.5
  of the quads.

## Download

[GitHub Releases](https://github.com/nyaLium/Font-Tweaks/releases)

## Configuration

`config/font-tweaks.json`, or Mod Menu → Font Tweaks:

| Option | Default |
| --- | --- |
| Bold rendering optimization / bold mode | on / dilation |
| Dilation strength / offset distance | 0.25 / 0.35 |
| Glowing sign outline optimization / outline distance | on / 16 blocks |
| Outline width | 1.0 px (range 0.5 – 1.0, step 0.1) |

## Compatibility

- Works with every vanilla glyph source: `BitmapProvider` (vanilla bitmap fonts), `TrueTypeGlyphProvider` (TTF from
  resource packs) and `UnihexProvider` (unifont — the fallback used for characters a font pack does not cover).
- Other glyph providers, unreachable pixels, failed GPU writes → automatically fall back to vanilla; text never disappears.
- No conflict with shaders (Iris) or Sodium — the vanilla text pipeline is not replaced.
- If another mod takes over text rendering (Caxton, Ember's Text API), bold optimization becomes inert and the sign
  outline automatically falls back to vanilla's 8 passes. A version mismatch degrades to vanilla behaviour instead of crashing.

## Build

```
gradle build --offline
```

## License

MIT (see `LICENSE`).
Inspired by the *ideas* of BoldTextFix (bold modes) and FeyTweaks (glow outline).
