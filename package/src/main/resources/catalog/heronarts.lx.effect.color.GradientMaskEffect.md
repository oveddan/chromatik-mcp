---
class: heronarts.lx.effect.color.GradientMaskEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/GradientMaskEffect.java
sourceSha256: c514642397b4b3962c9ccd919c675916f8463564c216486dbe849f4ad1f010db
classBytesSha256: 1c1e740ede053d42bae5e7f4e8beac543416c0c20ba144121a92fc9e0ac7e8a0
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, masking, spatial, geometric, palette
---

## Summary

GradientMaskEffect renders a full 3D spatial color gradient into an internal buffer using the same GradientPattern engine, then composites that gradient buffer against the incoming color buffer using a configurable blend mode. This lets a spatially-varying gradient — which can be animated, palette-linked, and mapped along any axis or combination of axes — act as a color filter or enhancement layer on top of existing channel content. A CUE toggle bypasses the composite and writes the raw gradient directly into the output for previewing gradient placement.

## Parameter interactions

All parameters from GradientPattern.Engine are exposed directly on this effect — axis selection, color stop configuration, gradient spread, and animation controls all work identically to the standalone pattern. The mode enum then determines how the generated gradient interacts with the underlying buffer: Multiply darkens the content by the gradient's color values (a black-to-white gradient becomes a spatial brightness mask); Add, Subtract, Spotlight, Highlight, Difference, and Lerp provide the full range of compositing options. Depth scales the blend alpha so the gradient influence can be dialed between none and full. CUE mode is useful to verify gradient layout before committing to a blend mode.

## Usage tips

GradientMaskEffect is the spatial counterpart to ColorMaskEffect — instead of a uniform color, it applies a position-varying gradient, making it ideal for giving a channel a spatial color identity (warm at the top, cool at the bottom) without replacing the underlying animation. Chained after a monochromatic noise or motion pattern, Multiply mode with a hue gradient creates the appearance that different spatial regions of the model have distinct colors. Because it reuses GradientPattern.Engine, any gradient configuration achievable in that pattern is also achievable here as a mask.
