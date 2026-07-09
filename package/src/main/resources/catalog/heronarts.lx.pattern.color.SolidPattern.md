---
class: heronarts.lx.pattern.color.SolidPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/color/SolidPattern.java
sourceSha256: 3618d9d360af191a84c537f21622ad8645b824adb6380d280146ead332acfa2c
classBytesSha256: 867902f1fcbc249c099e71bb7c5a266459fbd12be113b7cdb573e36008b99210
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, utility
---

## Summary

SolidPattern sets every pixel in the model to a single uniform color each frame using a LinkedColorParameter that can be pinned to a fixed HSB color or linked to a swatch in the active palette. The render loop is a single call to setColors with the computed color, making this the simplest and most CPU-efficient pattern in the library. When linked to a palette swatch, the output color tracks any live palette changes in real time.

## Parameter interactions

The color parameter is the only control. In fixed mode, hue, saturation, and brightness are set independently and the output is static until changed. In linked mode, the pattern delegates color resolution to the palette engine and the output changes whenever the active swatch changes. No interaction between parameters exists because there is only one.

## Usage tips

Use SolidPattern as a background layer to fill unlit regions with a consistent color, as a quick color-check for newly wired fixtures, or as the bottom layer in a channel stack where effects (blur, colorize, mask) do the heavy lifting. Linking to a palette swatch makes it a live "palette preview" pattern that respects global color cue changes. Avoid using it as the sole pattern in a channel during a live performance where dynamic visuals are expected — it is intentionally static.
