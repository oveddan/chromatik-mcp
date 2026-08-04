---
class: heronarts.lx.pattern.color.SolidPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/color/SolidPattern.java
sourceSha256: 3618d9d360af191a84c537f21622ad8645b824adb6380d280146ead332acfa2c
classBytesSha256: eb857ab705e704f5c399413a8c3d9bd17186f53d3cc4a27d33f27dd1e26e81e0
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.2/lx-1.2.2.jar
lxVersion: 1.2.2
generatedAt: 2026-08-03T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
tags: color, utility
---

## Summary

Sets every pixel to one uniform color per frame via a linked color parameter, either a fixed HSB color or linked to a swatch in the active palette.

- Rendering is a single set-colors call per frame — the cheapest pattern in the library.
- Fixed mode is a static, unchanging color; linked mode acts CONTINUOUSLY, tracking the active palette swatch live as it changes.

## Parameter interactions

- Color is the only control; fixed vs. linked mode determines whether hue/saturation/brightness are set directly or resolved through the palette engine every frame.

## Usage tips

- Use as a static background fill, a fixture-wiring color check, or the bottom layer under effects like blur/colorize/mask.
- Link to a palette swatch to make it a live "palette preview" that follows global color cues.
- Avoid as the sole pattern in a channel when dynamic visuals are expected — it never animates on its own.
