---
class: heronarts.lx.effect.color.ColorizeEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/ColorizeEffect.java
sourceSha256: a490feafccd4c48572b0145177dd8f7a9b18a42cdccb9c3b87b29e7f2e6e604c
classBytesSha256: f7573ddbc62cbd4910b16e986864963d95b9aa230d180008d21ed01929b98d57
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, masking, palette, utility
---

## Summary

ColorizeEffect remaps the color of every pixel in the buffer by extracting a scalar value from that pixel (brightness, luminosity, a single channel, the minimum channel, or the average) and using it as a lerp position along a two-stop or multi-stop color gradient. At partial colorization amounts the remapped RGB is blended in while the pixel's alpha channel is preserved; at full amount the gradient color replaces the pixel outright. The gradient can be drawn from two explicitly authored colors, derived relative to a base color by HSB offsets, linked to the active palette swatch with optional HSB adjustments, or pulled directly from the full palette gradient. A filter threshold can exclude pixels whose source value falls below a cutoff, leaving them unchanged, forcing them to black, or clearing them to transparent.

## Parameter interactions

The source mode determines what numeric value each pixel contributes to the gradient lookup; brightness and luminosity are the most perceptually intuitive, while red/green/blue or min/average modes let you extract colorimetric content as a mask driver. Amount scales the final blend between the remapped color and the original, so partial colorization is possible. In palette mode, paletteDepth compresses how far into the palette the highest-valued pixels reach, and paletteInvert reverses the mapping direction. The blend mode (RGB vs. HSB variants) controls how gradient color stops are interpolated, which matters most for hue-spanning gradients. Filter threshold combined with filterMode governs what happens to dark or near-black pixels that would otherwise land at the gradient origin.

## Usage tips

ColorizeEffect is the standard way to impose a palette on monochromatic or desaturated content — put it after a grayscale noise pattern or a brightness-only effect and the gradient remaps the luminance field into color. Using the Palette color mode lets it track the active swatch live, which is useful for show-wide color control. The filter threshold with Clear mode is powerful for compositing: it makes dark background regions transparent so the channel can be layered over other content in the mixer.
