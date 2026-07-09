---
class: heronarts.lx.pattern.texture.SparklePattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/texture/SparklePattern.java
sourceSha256: bc638a5ec2c448fbf1d9419bbf77048a05a4bae4ebf4d82ff76f570ac64a1122
classBytesSha256: 0619f63042b23de19862c6bac51f722a1d1efa1c69451c103a18995f1f613373
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: texture, generative, strobe
---

## Summary

SparklePattern animates up to 1024 independent sparkle generators across the model, each of which periodically selects a random set of pixel indices, fires a brightness pulse shaped by a selectable waveshape, and then picks new random targets. Sparkles fire at intervals drawn from a configurable speed distribution with optional per-sparkle variation, and the density parameter controls how many pixels are active simultaneously relative to model size. The output for each pixel is the accumulation of all active sparkles targeting it, added on top of a configurable base level, producing a field of randomly twinkling lights.

## Parameter interactions

Speed and the fast/slow interval bounds together define the temporal rate of sparkle cycles; variation randomly perturbs each individual sparkle's interval so they do not all synchronize. Density scales the number of pixels each sparkle addresses, spanning from a sparse scatter at low values to a near-full blanket at high values where the distinction between individual sparkles blurs. Duration as a fraction of each cycle controls how long the brightness pulse lasts within each period — at 100% the sparkle is on for its full interval, at 50% it fires and then goes dark before restarting. The sharp parameter applies a power-law transformation to the waveshape, making peaks narrower and more needle-like at positive values or softer and more dome-like at negative values. Min and max brightness bound the range of individual sparkle peak levels, with each sparkle drawing a random peak within that range on each cycle. Base level provides an ambient floor so the model never goes fully dark between sparkles.

## Usage tips

SparklePattern is best used as a texture overlay — apply it on its own channel at reduced channel level to twinkle over a base color provided by a GradientPattern or SolidPattern below. For a gentle shimmer, keep density low, speed moderate, and sharp negative; for an aggressive electric crackle, push density high, use the square or triangle waveshape, and increase contrast with a high sharp value. The pattern produces white-only output from its grayscale engine; colorize downstream with a ColorizeEffect or palette-driven effect to match the show's color palette. Because pixel selection is random per sparkle, the effect has no spatial coherence and works equally on any topology.
