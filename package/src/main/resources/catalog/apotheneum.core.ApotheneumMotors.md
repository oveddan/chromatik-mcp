---
class: apotheneum.core.ApotheneumMotors
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/core/ApotheneumMotors.java
sourceSha256: 53426f5b21488c9a126af944319e1aff17ccadaee53ff839c94fdaa80e9a54b9
classBytesSha256: d94cdbfbc1cfea3ecb38c6249404ae743d2faf1751ebfceec0834b05e0c50276
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, hardware-control, trigger
---

## Summary
Not a visual lighting pattern despite being registered as a pattern — it repurposes the color output channel to drive haptic motor hardware, not light.
- Every frame it sets all points to one flat grayscale value: a level-derived gray, or full black while brake is engaged.
- On an LED preview this reads as a flat uniform color; it is meant for an output routed to motor hardware.

## Parameter interactions
- Level (sampled continuously) sets the gray value, rounded to the nearest integer and used identically for R, G, and B.
- Brake is momentary and, while held/triggered, overrides level entirely and forces black — level has no effect during a brake.

## Usage tips
- Do not select this for a lighting look; use it only on an output specifically wired to motor hardware.
- Steer performers/AI clients away from it in normal content selection — on a standard LED output it just paints everything flat gray.
