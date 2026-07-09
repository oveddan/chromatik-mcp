---
class: heronarts.lx.effect.LinearMaskEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/LinearMaskEffect.java
sourceSha256: 8edbeb4a88537613058c0b6535c144a8667bce052d87e4d031b4cec5caab3ab5
classBytesSha256: 8c0f08247d552e0a1540c18f89b2b36a02d4934c5b7cf8879e7db21f8af3ca3e
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: masking, geometric, utility, color, gradient
---

## Summary

LinearMaskEffect attenuates or replaces the incoming color buffer according to a spatial gradient defined along one of the model's normalized axes (X, Y, Z, or radial distance from center). For each pixel it computes the distance from a reference offset position, constructs a linear falloff ramp starting at the mask size boundary and controlled by a fade width, and uses that ramp as an alpha value fed into one of two mask functions: Fade multiplies the pixel's color by the mask alpha to darken toward black, while Whiteout blends the pixel toward white by taking the lightest composite. The axis can be optionally rotated by yaw, pitch, and roll angles that are applied to a transformation matrix, and the mode selects whether the masking is symmetric around the offset (Abs), only affects points beyond the offset (Pos), or only affects points before it (Neg).

## Parameter interactions

Offset and size work together to position the fully-opaque region: offset moves the reference point along the axis while size extends the opaque zone away from that reference; increasing fade softens the edge between opaque and transparent. Switching between Abs and Pos/Neg modes changes the geometry from a centered fade to a one-sided wipe, which combined with the offset control lets you build curtain-like reveal transitions. The Invert flag flips which region is fully visible versus masked, turning a center-reveal into a center-erase. Enabling rotation decouples the mask axis from the model's raw geometry — rolling 45 degrees on a Y-axis mask produces a diagonal band. Fade Position (Outer/Inner/Middle) controls which end of the gradient is fully bright versus transparent, and FadeSize Relative scales the fade width proportional to the mask size so the transition remains consistent when scaling the mask.

## Usage tips

LinearMaskEffect is the primary tool for spatial windowing and edge vignetting on 3D LED models — use it to trim content away from the top, bottom, or periphery of a fixture without modifying the underlying pattern. A Y-axis mask with Neg mode and no offset creates a bottom-to-top fade that looks like content rising out of darkness. When combined with animated offset or size parameters driven by modulators, it produces sweeping reveal and wipe transitions. For performance, keep rotation disabled unless needed since it recalculates the transformation matrix each frame. Use Whiteout mask mode when adding to a composite where a white border is desired rather than a fade to black.
