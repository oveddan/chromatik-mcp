---
class: apotheneum.mcslee.FaceMask
kind: effect
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/FaceMask.java
sourceSha256: 24e040004c4638bc421f8326d236c5ea6a5b902ea017a46bec4f6e30ca960391
classBytesSha256: 096b6c3bd159b13604e45f1b0d8b0739e7f0136f310fc49a2f1dca45b11f96b2
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: masking, geometric, utility
---

## Summary

Independently dims or fully blacks out each of the four cube faces (front, right, back, left), applying the same mask level to both exterior and interior of a given face.

- One level per face controls that face's brightness via multiplication against existing content; a level of zero hard-sets the face to black instead of multiplying (a small optimization, not a behavioral difference at the extreme).
- Exterior and interior share the same per-face level — there is no separate interior control.
- The cylinder is untouched; this effect only affects the cube's four faces.

## Parameter interactions

- All four face levels are read continuously each frame and pre-multiplied by the effect's enabled amount, so fading the effect in/out smoothly scales all four masks toward full brightness together.

## Usage tips

- Use to selectively isolate or dim individual cube faces (e.g. spotlight the front face by lowering right/back/left toward zero) without affecting the cylinder.
- Because interior and exterior of a face are masked identically, this cannot be used to differentiate interior vs. exterior brightness — use a different effect for that.
