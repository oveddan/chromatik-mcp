---
class: apotheneum.mcslee.Crawlers
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Crawlers.java
sourceSha256: 3ef5a1476350b121da17fa19a93817a9077d5d9e6974c9f43250f1c1c5b8f37f
classBytesSha256: 484477c3378be9bf0fb7c3cbf9c07b5be1ff98f586dbbc64ac7fd269000fd86c
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, geometric, texture, generative
---

## Summary
- Animates up to 240 independent snake-like crawlers walking a wrapping grid on the cube exterior, and another up to 240 on the cylinder exterior, trails blended by brightest-pixel (not additive) compositing.
- Each crawler advances one grid step at a time in one of 4 axis-aligned directions, wrapping at grid edges on both axes; it turns randomly (gated by a minimum straight-run length) or can be force-turned horizontal/vertical by trigger parameters.
- A per-crawler activity level fades in/out over ~1s when its index crosses the active-count threshold, so changing active count fades crawlers smoothly rather than popping them.

```
loop:
  advance one grid step when its speed timer elapses; wrap x,y at edges
  maybe turn (only once steps-since-turn >= gate, then roll turn probability)
  ramp activity level toward on/off; draw trailing pixels with head/tail fade
```

## Parameter interactions
- Each crawler gets one fixed random percentile at spawn, but min/max/bias speed and length controls are re-applied to it every frame — since crawlers never respawn, sweeping min/max/bias live retunes every crawler immediately.
- Turn-gate (minimum straight steps) and turn-probability compose: high gate + high probability yields long runs punctuated by turns; low gate + high probability yields constant zig-zagging.
- The two turn triggers are one-shot broadcasts that reorient every crawler on the opposite axis on its next step, not a persistent constraint.
- Fade-head/fade-tail lengths are clamped to each crawler's current length, so short crawlers can be dominated by the fade regions.

## Usage tips
- Cube and cylinder crawler counts are independent, so the two components can carry different densities from one instance.
- Raising active-count live is safe (smooth fade-in); good for organic, insect/circuit-like texture across large or wrapping surfaces.
