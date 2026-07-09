---
class: heronarts.lx.effect.color.TransparifyEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/color/TransparifyEffect.java
sourceSha256: 862be53c0c7e0b20aa8f3e1e2f2bab5bbaf36e831baec71794be4d6bd3a39c4f
classBytesSha256: 441c9ef757030c245a9b60cc8f8e044dcd58316752f816593fc8709681854d6d
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: masking, color, utility
---

## Summary

TransparifyEffect reduces the alpha channel of pixels whose sampled scalar value (brightness, luminosity, or a single color channel) falls at or below a configurable threshold, effectively punching holes in the buffer based on pixel content rather than spatial geometry. Pixels above the threshold are left entirely unchanged, including their existing alpha. For pixels at or below the threshold, a feather parameter controls whether the alpha reduction is uniform across all sub-threshold pixels or graduated smoothly from zero reduction at the threshold down to full reduction at zero. The RGB values of affected pixels are preserved; only the alpha channel is modified.

## Parameter interactions

The threshold sets the ceiling of the transparency region: only pixels with a source value at or below the threshold are affected. Feather interpolates the alpha reduction: at feather=1 the effect smoothly grades from zero transparency change at the threshold down to full reduction at black (pixels exactly at the threshold remain fully opaque), while at feather=0 it applies the same uniform maximum transparency reduction to all sub-threshold pixels regardless of their exact value. Amount scales how aggressively alpha is pushed toward zero, allowing partial transparency rather than a full knockout.

## Usage tips

TransparifyEffect is the primary tool for making a channel's dark background transparent so it composites cleanly over other channels in the mixer without opaque black regions occluding content below. It is most useful at the tail of a channel's effect chain before the channel output feeds the mixer. Feather=1 produces the most natural look because near-threshold pixels fade out gradually; lower feather values create sharper keying. Choosing luminosity as the source mode responds to perceived brightness rather than raw channel maximum, which tends to produce cleaner keys on colorful content.
