---
class: apotheneum.mcslee.Logo
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Logo.java
sourceSha256: 29cf15cfc7b9dc148216cf0fedff7bf38d7f70add92cac61282b65152a354d8d
classBytesSha256: db5e0ca88efa6de9d0d46b0b2777736f4c16f589066040bc43ebe0554f8250ef
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, static, cube, texture
---

## Summary
- Renders a fixed logo mark — a square frame plus an inscribed circular ring — identically on all four cube exterior faces (geometry computed once per frame into a shared buffer, then reused per face), then copies to the interior.
- The square frame's size is a fixed constant in code, not exposed as a parameter; only the circle is tunable.
- Frame time is unused — this is a static mark, not an animated pattern.

## Parameter interactions
- Circle radius sets the ring's target distance from center; circle contrast sets both its brightness and falloff steepness (higher contrast = narrower, brighter ring) — both CONTINUOUS.
- Four independent per-face brightness levels (front/right/back/left) scale that face's copy of the identical logo geometry from off to full, without changing the shape.

## Usage tips
- Best as a static brand/identity treatment or a bumper between other patterns; drive per-face levels with an external modulator/blend for a fade-in/out reveal since the pattern itself never animates.
- Only the circle is resizable live — the square frame is fixed, so use per-face levels (not geometry) for asymmetric reveals.
