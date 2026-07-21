---
class: apotheneum.mcslee.DoorMask
kind: effect
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/DoorMask.java
sourceSha256: 412402eccac0de503ca9f6086369d57e7e1b1da4407474c4f37f08a53148e380
classBytesSha256: b573cf42ca9168af9399aff1b81a6b943bcd8881c1d9ea01debba546069815cc
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: masking, geometric, texture, utility
---

## Summary

Applies a soft brightness falloff mask centered on each doorway, dimming pixels near doors and leaving pixels far from doors unaffected (or the reverse, when inverted).

- Computes a per-pixel distance from the nearest door opening across all four surfaces (cube exterior/interior, cylinder exterior/interior) and multiplies existing colors by a mask derived from that distance.
- The mask shape blends between a diamond (averaged x/y distance) and a square (max of x/y distance) falloff, and can be stretched vertically relative to horizontal.
- Cue, SAMPLED as a boolean each frame, replaces colors with the raw grayscale mask instead of multiplying, for direct visual calibration.

## Parameter interactions

- Square, distance, ratio, contrast, and invert are read continuously each frame, so live sweeping smoothly reshapes the mask.
- Invert flips which side of the falloff is dark vs. light, swapping "mask near doors" for "mask far from doors."
- Contrast sharpens or softens the falloff edge; combined with distance it sets how large the darkened zone around each door is.
- Cue is momentary and overrides normal multiply blending for tuning; not meant to be left on during a show.

## Usage tips

- Enable cue while adjusting square/distance/ratio/contrast to see the exact mask shape before returning to normal blending.
- This effect always multiplies against existing content, so place it after the pattern/effects whose content should be dimmed near doors.
