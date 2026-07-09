---
class: heronarts.lx.effect.color.ColorMaskEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/ColorMaskEffect.java
sourceSha256: 16120f086cf57e45c8d6bff8a24210b73d5d06c9d75caee4bf0ec867b4cd7e5d
classBytesSha256: 57716e5b836c1639df708659852ab7bb8f01a11d4ee3bb9d997c4aadff348430
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, masking, utility
---

## Summary

ColorMaskEffect applies a single uniform color against every pixel in the buffer using a per-pixel blend function, uniformly tinting or filtering the entire channel output in one pass. The chosen color is resolved through a LinkedColorParameter, meaning it can be a static authored value or can follow the active palette swatch dynamically. The blend alpha is scaled by the depth knob multiplied by the effect's enabled amount, allowing smooth crossfades in.

## Parameter interactions

The mode enum selects the blend operator: Multiply attenuates the existing content by the chosen color (white mask is a no-op; darker colors reduce brightness and can introduce color casts); Add brightens content toward the mask color and can clip to white; Subtract darkens by subtracting the mask color channels; Difference produces an absolute-difference result that inverts near the mask color; Spotlight and Highlight preserve luminance relationships differently; Lerp crossfades every pixel directly toward the flat mask color. Depth scales the alpha applied to the blend, so partial amounts apply the chosen mode at reduced strength rather than the full operator.

## Usage tips

Use Multiply mode with a hue-saturated color to tint a channel without completely overriding its luminance content — a warm amber mask will color-shift bright white content while leaving near-black areas unaffected. Use Lerp mode with depth set below full to gradually push the entire channel toward a target color, which is handy for fade-to-color transitions. Because the color is a LinkedColorParameter it can be slaved to the palette swatch for show-wide color control without touching individual patterns.
