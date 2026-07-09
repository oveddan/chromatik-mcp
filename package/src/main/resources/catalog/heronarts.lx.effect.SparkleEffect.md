---
class: heronarts.lx.effect.SparkleEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/SparkleEffect.java
sourceSha256: f55a5652b85172950b2d767e28ddc0084a4129be78c5832ded126f1f086282ad
classBytesSha256: 261300a94172f29489ce522bde1dc86129636291eb9d255f7449ede182fbabf4
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: texture, sparkle, motion, masking, utility
---

## Summary

SparkleEffect composites a per-pixel random sparkle texture over the incoming color buffer using a configurable blend mode. It delegates animation to the shared SparklePattern.Engine, which independently advances a population of sparkling pixels at randomized intervals and brightness levels — each pixel in the model can sparkle asynchronously with its own timing drawn from speed, density, and variation parameters. The resulting per-pixel brightness level (0–100) from the engine is converted to a greyscale mask and blended against the input buffer each frame using one of several standard blend operations (Multiply, Add, Spotlight, Highlight, Subtract, Difference, Lerp). The engine continues advancing even when the amount parameter is zero, so re-enabling the effect always shows a live animation rather than a frozen frame.

## Parameter interactions

The engine's density controls how many pixels are sparkling at any moment, and speed together with the minInterval/maxInterval range controls how quickly each sparkle cycles through its waveform. Higher variation randomizes the per-sparkle timing so that dense configurations look organic rather than synchronized. The sharp parameter skews the sparkle waveform between smooth bell-shaped peaks and more abrupt on/off flashes. Min and max level set the brightness floor and ceiling for individual sparkles, keeping them above a minimum glow or below a maximum intensity. Switching blend mode changes what sparkles do to the incoming buffer: Mask mode (multiply blend) darkens everything but highlights where sparkles peak, while Add and Spotlight brighten the content at sparkle locations, and Subtract cuts holes in brighter regions.

## Usage tips

SparkleEffect with Mask mode (multiply blend) on a uniformly lit pattern produces the classic starfield shimmer where the pattern color shows through but randomly dims at sparkle locations. Add mode over a dark pattern creates the impression of twinkling light sources on a dark background. For a subtle glitter finish on a color pattern, keep density low and max level near 100 with Highlight blend, which only brightens where sparkle peaks exceed the underlying pixel. Because the engine runs independently of the amount parameter, disabling and re-enabling the effect produces no pop or freeze artifact. Chain after DynamicsEffect to apply sparkle to a shaped brightness surface rather than the raw pattern output.
