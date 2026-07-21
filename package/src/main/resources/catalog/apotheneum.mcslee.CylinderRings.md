---
class: apotheneum.mcslee.CylinderRings
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/CylinderRings.java
sourceSha256: 2ce7dd80998df192490ec05c93a7ac64af9875bdcb0eef8340eeff9e9f447151
classBytesSha256: 01a7a89422f84c6f42ae75e5836a65c79ea6ead34b5524f88e02f1bf8df6f6dd
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, geometric, minimal, utility
---

## Summary
- Draws a single soft-edged bright band that wraps horizontally around every ring of the cylinder exterior; brightness falls off linearly with angular distance from each ring's target angle.
- Each ring's target angle is the base position plus that ring's vertical index times a skew value, so nonzero skew staggers each ring's angle relative to the one below it, twisting the band into a diagonal or spiral; larger skew magnitude tightens the spiral, and its sign sets handedness.
- The finished cylinder exterior is copied onto the interior each frame.

## Parameter interactions
- Base position rotates the band uniformly — every ring's angle shifts together, continuously if modulated.
- Skew adds a per-ring offset proportional to ring index; at zero skew all rings share one angle (a straight vertical stripe).

## Usage tips
- Minimal building block: modulate position (e.g. an LFO) for a continuously rotating stripe, or modulate skew to animate the spiral's tightness/handedness.
- Draws exactly one band per ring — layer multiple instances with different position/skew and a blend mode to get more than one stripe or a crossing pattern.
