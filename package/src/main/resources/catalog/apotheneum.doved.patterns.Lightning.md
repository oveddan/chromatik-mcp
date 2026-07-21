---
class: apotheneum.doved.patterns.Lightning
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/Lightning.java
sourceSha256: 2ad4c8ae7533fa8b71fc130c2af0f0c1ff7dac81636350c7f52db6f71a573ea6
classBytesSha256: 40a92a780f912bd27bf6c4d3bb0f93e31732b5d8614bc5494a7cd661956ec2e4
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: strobe, midi, raster, geometric, motion, texture, trigger
---

## Summary
Renders a lightning bolt as a 2D raster (abstract top-to-bottom space) written to whichever cube faces are enabled, so one generated shape broadcasts across multiple faces.
- On trigger, one of four algorithms builds a segment tree top-to-bottom: midpoint displacement, an L-system walk, an RRT search, or a physically-inspired stepped-leader-plus-return-stroke model.
- Geometry is generated once per trigger, not per frame — only rendered opacity changes continuously via Fade times Intensity.

```
on trigger: segments = selectedAlgorithm.generate(params)
each frame: alpha = fade * intensity  // fade driven by an external envelope
  stroke each segment as an anti-aliased blue-white line;
  above a brightness threshold, alpha-blend a glow halo on top
```

## Parameter interactions
- Fade is meant to be driven by an external envelope (e.g. ADSR), read CONTINUOUSLY, while geometry is SAMPLED once at trigger; a bolt stays visible while Fade is above zero.
- Intensity is a separate static multiplier stacked on Fade.
- Only the active algorithm's own parameters apply; Branch (probability) is used by both Midpoint and RRT, but Branch Dist and Branch Angle feed Midpoint only — RRT branch geometry is a random angle with length derived from its own step-size parameter, so tuning Branch Dist/Angle while in RRT mode has no visible effect.
- Bleeding controls glow-halo strength uniformly across all four algorithms.
- Depth (midpoint), RRT Max Iter, and LS Iterations trade detail for generation cost.

## Usage tips
- Generation only happens on trigger; rapid retriggering makes a fresh, differently-shaped bolt each time — drive visibility with Fade, not retriggering.
- Face-selection controls mirror one bolt onto multiple faces; leave interior off for exterior-only strikes.
- RRT and Physical are the most compute-heavy per trigger — favor Midpoint for snappier response under rapid triggering.
