---
class: apotheneum.mcslee.Quilt
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Quilt.java
sourceSha256: ebeda8160dc0f1f055c4ab012dac017238536059b384219d7937af4599eda794
classBytesSha256: 56e1a2ce8f59b024c918a0400fdcd4a66df7a7c41e4868493f077317ff16d494
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, color, geometric, texture, cube, cylinder
---

## Summary
- Weaves a "quilt" texture from many independently moving stripes: one vertical stripe per column of cube+cylinder exterior, plus horizontal stripes per row/ring (3 per row on the cube, 2 per ring on the cylinder, matching their differing aspect ratios).
- Each stripe is a short color band with linear falloff that travels along its column/ring, moving forward on even indices and reverse on odd, wrapping at the strip's length; overlaps composite with a lightest-color blend, so crossings show whichever thread is momentarily brighter.
- Vertical and horizontal stripe families use fully independent HSB colors, so crossing threads can be complementary or matching.

## Parameter interactions
- Each stripe's speed and length come from the same min/max/bias weighting used by the Crawlers pattern: a live min/max/bias triple, skewed toward one end by a random draw SAMPLED once when the stripe is created (stripes are never re-spawned) — that draw sets each stripe's fixed position within the range, while the min/max/bias parameters themselves are CONTINUOUS and reread every frame, so sweeping them live reshapes all stripes' speed/length in real time.
- Horizontal and vertical color (hue/saturation/brightness) are set independently per family.

## Usage tips
- Equal min/max speed with centered bias gives a calm, uniform crawl; wide min/max with an off-center bias gives a chaotic, varied weave.
- Use contrasting H/V colors to make the woven crossing structure legible, or matching colors for a subtler brightness-only texture.
- Always renders across the full cube+cylinder exterior — works well as a base/ambient layer under a sparser foreground pattern.
