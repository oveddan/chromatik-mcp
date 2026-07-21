---
class: apotheneum.mcslee.DNAHelix
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/DNAHelix.java
sourceSha256: cdfd6ae47ab7e00c1760d2167d3f6391f7e8189bea2092fa4a8da1dfc7719828
classBytesSha256: 2b203a72d96e27411b988dc254a62e648e1888e5f16f73c7e39995e01f72ad43
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, geometric, texture, noise
---

## Summary
- Renders one continuous winding stripe per enabled component (not a literal two-strand helix) spiraling around the cylinder exterior and/or the cube exterior's rings, each toggled independently.
- Each ring's target angle is a twist offset plus vertical index scaled by an effective winding coefficient, perturbed by continuously-evolving 2D noise; brightness falls off linearly from that angle, giving a soft adjustable-width stripe.
- The two noise-driving LFOs run continuously regardless of noise amount, so the field keeps evolving even at zero visible influence.

```
turnRate = spiralTurns * (1 - noiseAmount)^2   // winding rate fades as noise rises
targetAngle(ring) = twistPhase + ring.index * turnRate + noiseAmount * perturbStrength * noise2D(...)
```

## Parameter interactions
- Winding and noise amount interact multiplicatively: raising noise quadratically suppresses the geometric spiral, trading it for a noise-dominated wandering band; winding is fully expressed only at zero noise.
- Twist continuously animates the spiral's phase (suited to LFO modulation); winding sets a fixed turn count, independent of twist.
- Noise depth sets perturbation strength (negative inverts it); fall/morph rates control field scroll/reshape speed — nonzero rates keep the stripe writhing even with twist and winding static.
- With both enabled, "alternate" flips only the cube's winding sign and "offset" shifts only the cube's phase, relative to the cylinder's; both inert while cube is off.

## Usage tips
- Cylinder-only gives a classic single spiral; both with alternate on and nonzero offset gives two independently-phased, oppositely-wound spirals.
- Calm motion: noise near zero, animate twist. Organic writhing: raise noise amount/depth, rely on the always-running fall/morph LFOs.
- The noise LFOs never stop, so raising noise amount mid-show reveals wherever the field has already drifted, not a phase-zero start.
