---
class: apotheneum.mcslee.DoorEmanation
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/DoorEmanation.java
sourceSha256: dcac35157052547c5ca632597b29ab6af1e4607f10f37e04a3b0a7382d06b316
classBytesSha256: 36a5230663029ff85124f44418a1e59ddfda643b491e08f5775e9d9b87c4323e
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, geometric, cube, cylinder, texture
---

## Summary
- Emits short fading "spark" streaks that radiate from each of the four cube doors and the cylinder door, running up the columns above each door and sideways along the rows flanking it.
- Each strip is cleared to black then composited with a lightest-color blend; the finished cube+cylinder exterior is copied to both interiors.

## Parameter interactions
- Each strip draws a random rate between min/max rate, SAMPLED once at spawn and re-sampled whenever the strip completes a cycle — the population desyncs into a spread of speeds instead of moving in lockstep.
- A bipolar speed control is CONTINUOUS: scales overall pace live, and its sign sets both direction (inward/outward) and which end a re-spawned spark restarts from.
- Density gates how many of up to 10 sparks per strip are active; a strip's "on" state is SAMPLED at spawn/cycle-restart from the live density value, not rechecked mid-flight.
- Cube and cylinder each have independent vertical/horizontal reach (length) controls, so streak lengths can differ per geometry.
- Fade sets falloff width; a momentary "wait" toggle holds new spark starts (in-flight sparks finish) until released — useful for staging a synchronized release.

## Usage tips
- Reads as light escaping the door frames — pairs well with keeping the doors themselves dark.
- Raising density gradually thickens the effect without an obvious synchronized pulse, since activation is randomized per strip.
- Use independent cube/cylinder length knobs for asymmetric reach (e.g. short cube bursts, long cylinder trails).
