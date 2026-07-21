---
class: apotheneum.doved.patterns.Fireflies
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/Fireflies.java
sourceSha256: a294104afd9b509280cfeb30987a1a885749add4acecd957effa388aa2b1e49a
classBytesSha256: 3a8bf2df66f6599a4a6396394eb85bf2166d8d8de22a5f2668a6a98b797f603e
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, glow, texture, wander, ambient
---

## Summary
Spawns and animates independent glowing points wandering across both cube and cylinder ring surfaces and both orientations simultaneously (shape assigned randomly per firefly at birth).
- Each firefly has its own randomized lifespan, speed (biased slower), and pulse rate; heading is horizontally biased, bounces off bounds and door areas, and wraps horizontally.
- Brightness combines an age-based fade-in/out envelope with a two-frequency sine pulse, rendered as a bright core with a configurable radial falloff glow, additively blended. Population continuously tops up toward a target as fireflies age out.

## Parameter interactions
- Quantity sets the target population, approached by spawning in bursts scaled to the current deficit rather than snapping to target each frame.
- Glow Size sets the radius searched around each core pixel; Glow Focus is a power-curve exponent on the falloff — large radius + high focus gives a tight bright core with a long dim tail; small radius + low focus gives an evenly-bright blob (both CONTINUOUS).
- Spawn rate, speed range, lifespan range, pulse rate/depth, and wander strength are fixed internal constants, not exposed — busyness/flicker beyond Quantity/Glow isn't tunable.

## Usage tips
- Renders on both shapes and orientations unconditionally — always lights the full structure; layer a mask on top for single-shape coverage.
- Dense fireflies (high Quantity, default Glow Size) wash into a diffuse haze; lower either to keep individuals legible.
- Good as a low-key ambient/idle pattern given its slow, non-repeating, self-managing turnover.
