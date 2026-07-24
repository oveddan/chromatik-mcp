---
class: heronarts.lx.pattern.color.GradientPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/color/GradientPattern.java
sourceSha256: fa36c9071e5ca752df1e86757c3c478984b5af0782062e8c6659506fd5e4c919
classBytesSha256: ef225bc8e0ed51f529887ac59927e1ac07dd46ca0160d492fb00057eb2dab1e6
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
curated: parameterInteractions, usageTips
curatedAt: 2026-07-24T00:00:00Z
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

## Usage tips

- Works well as a static or slow-moving color backdrop beneath texture or motion patterns.
