---
class: heronarts.lx.effect.HueSaturationEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/HueSaturationEffect.java
sourceSha256: 1564847e34c33a1ebe99e2d7061a75dbb3c65caa23e7ac1acd40b28d47eebd3f
classBytesSha256: 9578b525d654c29a1336b56c96b9b15c3600d5056dba5303326dfe7c83792277
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.2/lx-1.2.2.jar
lxVersion: 1.2.2
generatedAt: 2026-08-03T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
tags: color, hue, saturation, brightness, utility
---

## Summary

Adds fixed hue/saturation/brightness offsets to every pixel in HSB space.

- Hue offset rotates all colors by a fixed number of degrees; wraps continuously, so a 180° offset yields exact complements.
- Saturation and brightness offsets are added then clamped to their valid range, not scaled — pushing brightness to its floor makes hue and saturation offsets invisible regardless of their values.
- The effect's enable amount CONTINUOUSLY cross-fades all three offsets back toward the original HSB values as amount drops below full, rather than snapping.

## Parameter interactions

- Hue shift has no visible effect on already-achromatic (zero-saturation) pixels, since grey carries no hue angle.
- Negative saturation combined with a positive brightness offset produces a fade-to-white; negative saturation alone desaturates toward grey.
- Pixels already at maximum saturation or brightness show no further change from a positive offset in that channel (clamped, not wrapped or overshot).

## Usage tips

- Use for palette recoloring without touching the source pattern: a fixed hue offset uniformly rotates any multicolor gradient.
- Animate hue with a slow LFO for a continuous color-cycle on top of any pattern.
- Push saturation fully negative to convert any pattern to greyscale in place.
