---
class: heronarts.lx.pattern.color.GradientPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/color/GradientPattern.java
sourceSha256: fa36c9071e5ca752df1e86757c3c478984b5af0782062e8c6659506fd5e4c919
classBytesSha256: 8600ae2e03c67da55dddb3d458b192f7c736d98851eee5ae951c2b7579557744
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.2/lx-1.2.2.jar
lxVersion: 1.2.2
generatedAt: 2026-08-03T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
curated: parameterInteractions, usageTips
curatedAt: 2026-07-25T00:00:00Z
tags: color, gradient, palette, geometric, generative
---

## Summary

Fills the model with a continuous gradient computed from a weighted sum of per-axis spatial coordinates, looked up against a color ramp.

- Color mode selects the ramp source: Fixed (static color pair), Linked (a palette swatch plus a hue/saturation/brightness delta), or Palette (interpolates across N palette swatches).
- Each axis has an independent amount, offset, and coordinate mode (Normal, Center-folded, or Radial), applied CONTINUOUSLY per frame; amount's sign also selects that axis's inverted coordinate function.
- A compression control normalizes combined per-axis amounts so multiple full-strength axes don't clip the gradient at its endpoints.

## Parameter interactions

- In Fixed and Linked modes the master amount multiplies the hue, saturation, and brightness ranges into the delta defining the ramp's second stop — at zero it nulls all three however far they are turned, collapsing the ramp to one color.
- Palette mode builds its stops from swatches, ignoring the master amount and all three ranges; per-axis amounts still apply in every mode.
- A fresh instance renders flat because the master amount and every per-axis amount start at zero: Fixed and Linked need both raised, Palette only a per-axis amount.
- Only a Y-axis amount gives a top-to-bottom gradient; equal X/Y amounts a diagonal sweep; Radial mode on all axes a spherical gradient from center.
- A scale control zooms the coordinate range before lookup; with wrap or mirror clamping this produces repeating bands rather than one sweep.
- A phase offset shifts the lookup position and is the natural animation target — an LFO on it gives a scrolling wash, where animating a per-axis amount reshapes geometry instead.
- Center coordinate mode mirrors the gradient around an axis's midpoint.

## Usage tips

- Works well as a static or slow-moving color backdrop beneath texture or motion patterns.
- Combining more than two axes at full amount without compression clips the gradient at its color-stop endpoints, losing the smooth blend in the middle.
- Animate phase for a scrolling wash rather than per-axis amount, which reshapes geometry instead of moving it.
