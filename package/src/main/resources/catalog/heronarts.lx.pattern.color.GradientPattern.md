---
class: heronarts.lx.pattern.color.GradientPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/color/GradientPattern.java
sourceSha256: fa36c9071e5ca752df1e86757c3c478984b5af0782062e8c6659506fd5e4c919
classBytesSha256: ef225bc8e0ed51f529887ac59927e1ac07dd46ca0160d492fb00057eb2dab1e6
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, gradient, palette, geometric, generative
---

## Summary

GradientPattern fills the model with a continuous color gradient distributed across one or more 3D spatial axes. For each pixel, the pattern computes a weighted sum of the pixel's normalized X, Y, and Z coordinates (using selectable per-axis coordinate modes such as normal, center-folded, or radial), looks up the resulting value in a gradient derived from a fixed color pair, the live palette, or a palette slice, and writes that color to the pixel. The gradient can be scaled, phase-shifted, inverted, and clamped or wrapped at its boundaries, and the entire coordinate space can be rotated with yaw/pitch/roll controls to orient the gradient at any angle through the model.

## Parameter interactions

The per-axis amount parameters are the primary shape controls: setting only Y-amount creates a top-to-bottom gradient, combining X and Y at equal weights produces a diagonal sweep, and enabling radial coordinate mode for all axes creates a spherical radial gradient from the center outward. The compress parameter prevents over-saturation of the gradient when multiple axes are combined by normalizing the total amount. Gradient scale multiplies the range of the coordinate before color lookup, effectively zooming in on the gradient and creating repeating bands when combined with wrap or mirror clamping. Phase shifts the lookup offset, enabling animated motion when driven by a modulator. In palette mode the gradient interpolates across a configurable number of swatch stops, making the output follow live palette changes.

## Usage tips

GradientPattern works well as a base layer beneath texture or motion patterns, providing palette-consistent color backdrop without any animation of its own. For slow animated washes, attach a SawLFO to the phase parameter. The center coordinate mode on any axis creates a symmetric mirrored gradient that reads well on installations with a visible center plane. Avoid combining more than two axes at full amount without enabling compress, otherwise the gradient clips at the color-stop endpoints and loses the smooth blend in the middle of the model.
