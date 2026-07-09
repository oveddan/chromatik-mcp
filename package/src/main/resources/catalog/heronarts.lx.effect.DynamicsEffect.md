---
class: heronarts.lx.effect.DynamicsEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/DynamicsEffect.java
sourceSha256: b1a1d5bab76897446146a465f3b916da36c6fe73bf0f8ffd919f49537846fe1d
classBytesSha256: 9b9ffeaf5a2140b4a4a31899bcc5d758f33fd9503ce5be6ddb9af932349175a2
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, utility, brightness, contrast, gain
---

## Summary

DynamicsEffect remaps each pixel's RGB channel values through a precomputed 256-entry lookup table, reshaping the brightness response curve of the incoming color buffer without touching hue or saturation directly. The transform pipeline is: gate cuts any input below a threshold to zero (with a rescaling factor to preserve dynamic range above the gate), contrast applies a symmetric S-curve or inverse-S around the midpoint of the gated result, shape then applies a power-law curve that bends the contrast-adjusted response toward highlights (positive) or shadows (negative), gain multiplies the result (with exponential scaling), and floor/ceiling clamp the output range. Per-channel red/green/blue amount controls then lerp each channel independently between its original value and the fully processed value, enabling selective channel brightening or tinting through differential dynamics.

## Parameter interactions

The gate and contrast parameters interact first: raising the gate zeroes the darkest inputs, and contrast then bends the S-curve on the surviving range — high positive contrast will sharpen the gated response into a near-step function. Shape then operates on the already-contrasted curve, bending it further toward highlights or shadows; setting shape after a high-contrast pass can dial back its sharpness or push it into an extreme power curve. Gain is applied after all curve shaping, so very high gain values clip aggressively unless ceiling is lowered to compensate. Setting floor above zero lifts blacks, which defeats gate effects but is useful for ensuring minimum brightness across all pixels. The per-channel amount sliders allow the dynamics to affect only certain channels — for example, applying full dynamics to red and blue while leaving green at zero shifts the color balance of whatever survives the curve, which can be used for color-grading alongside contrast shaping.

## Usage tips

Use DynamicsEffect after a pattern to punch up contrast on dim patterns or to suppress low-level noise by gating it out. It is particularly effective when chained after effects that produce gradients — the shape curve can convert a gentle linear gradient into a sharp edge or a soft vignette. When building audio-reactive rigs, driving the gate parameter from an audio modulator creates a noise-gate effect where ambient idle patterns are suppressed and only peaks break through. Avoid stacking multiple instances with high gain, as the LUT is applied to already-processed values and successive gain boosts will clip most of the buffer to maximum brightness.
