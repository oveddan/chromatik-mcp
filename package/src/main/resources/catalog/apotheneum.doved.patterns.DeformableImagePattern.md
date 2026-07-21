---
class: apotheneum.doved.patterns.DeformableImagePattern
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/DeformableImagePattern.java
sourceSha256: 7a2aa74ae3ff7bb9cfc10e1c413324970a556402a63b54d62b8c7af37afbabf3
classBytesSha256: 41a7bdcba0dfd237e659805768cdde4fb12923c78344b4cf4f20c35082334012
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: image, texture, geometric, motion, utility, masking
---

## Summary
Wraps an image/GIF renderer with an optional radial-mirror (kaleidoscope) deformation applied before the standard image transform (position/rotation/scale/scroll/image-mode); at minimum segment count it behaves like a plain image pattern.
- Renders to every point in whatever model is loaded, not scoped through Apotheneum's cube/cylinder helpers — no built-in per-face exterior/interior toggle.

```
for each point:
  p' = kaleidoscopeFold(p.position, center, segments, rotation)   # wedge mirror/repeat
  p' = imageTransform(p')        # translate/rotate/scale/aspect
  color = sampleImage(p', imageMode, scroll)  # background color if out of bounds or no image
```

## Parameter interactions
- Segment count is continuous; at its minimum it disables folding (raw passthrough), higher values tile that many mirrored wedges around the circle.
- Kaleidoscope center (3D) sets the fold's pivot — moving it shifts where wedges converge; the two rotation angles spin the wedge pattern in place.
- Folding happens before the image transform, so rotating/scaling the base transform spins or scales the already-kaleidoscoped field, not the source image content underneath it.
- Image mode (clamp/clip/tile/mirror) governs out-of-bounds sampling as in a stock image pattern; tile or mirror gives seamless tiling at wedge boundaries.

## Usage tips
- Reach for this for image/GIF content with an optional radial-mirror treatment; leave segment count at its minimum for an ordinary image pattern.
- No per-face routing — paints the whole model, so pair with masking/blend effects for a surface-limited look on Apotheneum.
- Fold math divides by distance from the kaleidoscope center, so behavior is only meaningful once that center sits inside the model's coordinate space.
