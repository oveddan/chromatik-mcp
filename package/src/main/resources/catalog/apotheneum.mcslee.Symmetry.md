---
class: apotheneum.mcslee.Symmetry
kind: effect
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Symmetry.java
sourceSha256: 6218a388890e97c2fe18a7338e46da9eea9fcc8eded87ace9029b6588ff7131e
classBytesSha256: 002581bcbf188098f967e1093f3918e649da74b6a2131c396ca9baf0c8089d44
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, motion, texture, utility
---

## Summary

Imposes radial (around-the-surface) and/or horizontal (top/bottom) mirror or repeat symmetry on the cube and the cylinder, each with its own segment count and split angle, but a single shared reflection style.

- Radial symmetry divides a surface's columns into N segments from a chosen angle, copying the first segment into the rest, mirrored or repeated; cube and cylinder have independent segment counts and angles.
- Reflection style (mirror vs. repeat) is governed solely by the cube-side mode and applied to both the cylinder and cube passes; the cylinder's own reflection-mode control is registered but never read, so changing it has no visible effect — set reflection style via the cube control only.
- Horizon symmetry, independently toggleable per surface, folds rings above/below a chosen row back and forth across the available span, with a toggle to invert fold direction.
- Damping is disabled, so parameter changes apply as instant recomputation, not smoothed transitions.

```
for each enabled surface (cube, cylinder):
  if segments > 1: copy first segment into the rest from a live angle offset (flip alternating segments if mirror mode)
  if horizon enabled: fold rings outward from a live center row, alternating fold direction each pass through the span
```

## Parameter interactions

- Segment counts come from a fixed list of surface-appropriate divisors, so only certain counts are reachable, not arbitrary integers.
- Reflection mode: mirror flips alternating radial segments (symmetric fan); repeat duplicates them as-is (rotational tiling) — this choice applies identically to both surfaces regardless of the cylinder's own mode control.
- Angle parameters are CONTINUOUSLY read each frame, so sweeping them rotates the pattern live, independently per surface.
- Cube and cylinder horizon toggles are independent and combine freely with radial symmetry on the same surface in one pass.

## Usage tips

- Expect only clean fractional splits (e.g. quarters, eighths) to be selectable, not arbitrary segment counts.
- Damping is off, so fast-modulating angle or horizon position produces visible per-frame jumps, not smooth motion.
- Apply radial symmetry to turn an asymmetric source pattern into a repeating or mirrored motif without modifying the source.
- Do not expect the cylinder reflection-mode control to change cylinder behavior independently of the cube — it is currently inert; toggle the cube's mode to change both surfaces' reflection style at once.
