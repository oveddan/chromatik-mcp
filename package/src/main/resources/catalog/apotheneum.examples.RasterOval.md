---
class: apotheneum.examples.RasterOval
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/examples/RasterOval.java
sourceSha256: a2258efd5899f6f46eda130a812e4888cc5e105029727d9217e69fab88743530
classBytesSha256: 5e75b0eeeb5f83a697cdeef1a259ee69cde116797bbbeeddad8f6983993c6476
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, texture, utility
---

## Summary
Example pattern built on the shared 2D-raster pattern base class.
- Each frame clears an off-screen raster, draws one solid red filled oval anchored at the raster's top-left corner, then copies the identical raster image onto whichever cube faces (front/right/back/left, exterior and/or interior independently) are enabled — same content on every enabled face, not per-face.
- Only ever writes to the cube; cylinder surfaces are untouched regardless of settings.

## Parameter interactions
- Width and height are sampled continuously and set the oval's bounding box as an independent fraction of raster width/height.
- The oval is anchored at (0,0), not centered, so increasing either control grows the oval outward from the top-left corner rather than symmetrically.
- The eight inherited face-target toggles only control which face/surface combinations receive the raster; none affect the oval's shape or position.

## Usage tips
- Primarily a teaching example for the raster-pattern base class (Graphics2D rendering plus per-face routing), not a polished look — fixed red color, no position control.
- Treat as a reference for building custom raster patterns rather than standalone performance content.
