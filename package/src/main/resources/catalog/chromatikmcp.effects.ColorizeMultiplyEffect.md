---
class: chromatikmcp.effects.ColorizeMultiplyEffect
kind: effect
sourceRepo: chromatik-mcp
sourcePath: package/src/main/java/chromatikmcp/effects/ColorizeMultiplyEffect.java
sourceSha256: 813ff11bfae5033e6b6cddca970810dd11c8e844651d6d8abc188b707c93451f
classBytesSha256: c2a9b370f330cc031600099ced9b6eb5046f07d133609998e6d99bcd06f9e472
classBytesOrigin: package/target/classes
lxVersion: 1.2.2
generatedAt: 2026-08-11T18:52:32Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
tags: color, gradient, palette, brightness, utility
---

## Summary
Colorizes each pixel by looking up a gradient color indexed on that pixel's own brightness (or luminosity), then scales the looked-up color back down by that same brightness value.
- Unlike stock `ColorizeEffect`, which discards source brightness after the lookup, this multiplies the result back in — a brightness-0 source pixel always renders black, regardless of what color sits at gradient position 0, so a full multi-color gradient can be used without lighting up "off" regions.
- `Depth` (0-1, CONTINUOUS) is a superset knob: `out = lerp(lookupColor, lookupColor * brightness, depth)`. At `depth=0` this effect is byte-for-byte identical to `ColorizeEffect`'s lookup-only behavior; at `depth=1` it is the full brightness-preserving multiply. Intermediate depths are a partial blend toward black, not a partial guarantee of darkness — only `depth=1` guarantees a brightness-0 pixel is exactly black.
- Reuses the same gradient machinery as `ColorizeEffect` (Fixed/Relative/Linked/Palette color modes, RGB/HSV/HSV-Min/HSV-CW/HSV-CCW blend modes), but exposes only two source modes (Brightness, Luminosity) instead of stock's eight.

## Parameter interactions
- `Threshold`/`Threshold Mode` gate the *low end only* and deliberately do **not** rescale the brightness index above the cutoff, unlike `ColorizeEffect`'s `filterThreshold` (which stretches everything above threshold back to the full 0-1 range, silently remapping every hue whenever the threshold moves). Moving `Threshold` here never changes the color of a pixel that stays above it — only which pixels get gated.
- `Threshold Mode = Clear` fades the gated pixel's alpha to 0 (transparent, so lower layers show through) while leaving its RGB untouched; `Black` zeroes RGB and keeps alpha; `Leave` skips the pixel entirely, alpha and RGB both untouched.
- `Amount` (CONTINUOUS) crossfades the whole computed result — RGB and alpha both — back toward the unmodified source, same convention as `ColorizeEffect`'s `Amount`.
- `Blend Mode` defaults to RGB (cuts straight through the color cube, never traversing unrelated hues) rather than HSV — appropriate for wide-arc gradients where an HSV path would visibly detour around the color wheel.

## Usage tips
- Use `depth=1` when the goal is to recolor a pattern while preserving its brightness structure (dim regions stay dim, bright regions stay bright) — this is the effect's whole reason for existing over stock `ColorizeEffect`.
- Use `depth=0` only to reproduce classic `ColorizeEffect` behavior (flat hue-per-brightness-band, darks not preserved) while keeping this effect's extra threshold/source options.
- A gradient whose first stop is a fully saturated, non-black color is the case this effect is built for — with stock `ColorizeEffect`, that stop would make brightness-0 pixels glow; here they stay black at `depth=1`.
- Pair `Threshold Mode = Clear` with a layer beneath this effect (channel blend or group) to let near-black source noise show the layer underneath instead of being stamped black.
