---
class: apotheneum.doved.patterns.Boids
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/Boids.java
sourceSha256: 67165dc7e3b16648c336eeeb1b073a8e3b05ab335fd4c32c56d43bc490845ed0
classBytesSha256: 6e915e9276fcde1896f4a2d80bb69b521c3d557148133798eb2d47c5e113fa48
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, flocking, texture, geometric, wander
---

## Summary
Classic boids flocking rendered as white dots on the cube or cylinder ring surface (one shape at a time), using a spatial hash grid rebuilt every frame for neighbor lookups.
- Combines separation/alignment/cohesion with turbulence and a fixed force in the door row band, clamped and integrated into velocity/position.
- Moves in a space extended beyond physical bounds to reduce edge bunching, but only physical-height pixels render; boids blend additively, and per-frame brightness decay leaves trails.

## Parameter interactions
- Max Flock sets the pooled count; Density gates what fraction actually simulates/renders — raising Max Flock alone adds nothing unless Density also rises.
- Radius resizes the spatial grid cell size, affecting both behavior and lookup cost.
- Separation/Alignment/Cohesion are independent CONTINUOUS multipliers — zero Cohesion with high Separation gives a diffuse scatter, not tight flocks.
- Blur is a per-pixel brightness-retention scale (CONTINUOUS): 0 hard-clears, near-1 leaves long streaks.
- In this y-down convention, the door-band force pushes boids further downward rather than away from doors, despite an inline comment claiming "upward" — verified from the force's sign.
- Switching Shape rescales existing positions into the new coordinate space rather than respawning, roughly preserving formation.

## Usage tips
- The extended buffer zone never renders — it only prevents bunching at the real edges.
- High Blur suits a soft trail; low Blur with high Brightness suits a crisp swarm.
- Radius interacts non-linearly with the force weights — retune it first before chasing tightness with the weights.
