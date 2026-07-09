---
class: heronarts.lx.effect.HueSaturationEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/HueSaturationEffect.java
sourceSha256: 1564847e34c33a1ebe99e2d7061a75dbb3c65caa23e7ac1acd40b28d47eebd3f
classBytesSha256: bc704abc82e2c3d8eb0ba7ab2aead00ed4c45e7f5a583d33099b15357c3d5afa
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, hue, saturation, brightness, utility
---

## Summary

HueSaturationEffect shifts each pixel's hue, saturation, and brightness by additive offsets in HSB color space, converting each incoming RGB value to HSB per-pixel, adding the configured deltas, clamping saturation and brightness to the 0–100 range, and converting back to RGB. Hue wraps naturally as degrees, so adding 180 degrees flips all colors to their complements. When the effect's blend amount is less than full, all three adjustments are linearly interpolated back toward the original HSB values before conversion, preserving smooth fade-in behavior.

## Parameter interactions

The three parameters are independent additive offsets: hue rotation has no effect on achromatic (zero-saturation) pixels since grey has no defined hue angle, and a brightness offset that drives pixels to 0 will make hue and saturation adjustments invisible regardless of their values. Saturation can be pushed negative to desaturate, which combined with a brightness boost produces a fade-to-white. Hue shift combined with reduced saturation shifts palette without washing out color completely — useful for subtle palette drift. Because conversion is per-pixel in HSB, pixels that were already at maximum saturation will not increase further when the saturation parameter is positive.

## Usage tips

HueSaturationEffect is the go-to for palette recoloring without redesigning a pattern: applying a fixed hue offset rotates the entire palette uniformly, so a multicolor gradient becomes the same gradient offset by a fixed angle. Animating the hue offset with a slow LFO creates a living color cycle across any pattern. Negative saturation converts any colored pattern to greyscale when pushed fully negative, and combined with selective brightness boost produces a faded or bleached look. Chain after effects that produce high-contrast output to add color back in, or place before InvertEffect to invert a hue-shifted palette.
