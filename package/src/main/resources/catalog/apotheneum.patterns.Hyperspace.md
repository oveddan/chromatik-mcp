---
class: apotheneum.patterns.Hyperspace
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/patterns/Hyperspace.java
sourceSha256: 3924fc5a71213190a9ae21946ba7a177b4b7392cbd773436fec96398e29ff726
classBytesSha256: 65d63694f021d39f773ac2b11d1bfbb7af45d10c17dfa95e2f6e6c06a4156a66
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, geometric, texture, particles
---

## Summary
3D star-field warp/travel effect: up to 5000 pooled white stars spawn just behind the installation on a single motion axis, translate only along that axis each frame, and fade in/out over the first/last tenth of life.
- Each frame clears colors, then blends each live star additively into its nearest LED (found via a spatial grid) — and, for cube LEDs, also into the matching point on the opposite orientation (exterior mirrored to interior).
- Cube faces perpendicular to the motion axis are skipped when finding nearest LEDs (X skips left/right, Z skips front/back); Y renders all four faces. Cube and cylinder can each be independently excluded.

## Parameter interactions
- Speed and brightness act continuously; an optional pulse toggle modulates speed sinusoidally for a breathing feel.
- Density (spawn rate) and duration (lifespan, randomized 50-150% per star) are continuous and together roughly set how many stars are visible, capped by the 5000-star pool.
- Axis/direction jointly set spawn location and which cube faces are eligible for rendering — changing axis while cube rendering is on immediately changes which faces go dark.
- Momentary Clear resets active star count and the spawn accumulator without discarding pooled objects, restarting the field instantly.

## Usage tips
- Switching axis reads as a hard cut in flow direction and participating faces, not a smooth turn.
- High density with long duration saturates the 5000-star pool.
- Cube + cylinder together shows visibly different surface densities as stars cross the bounding cube; disable one for a cleaner look.
- Logs verbose performance stats every 60 frames — diagnostic noise, not performer-facing.
