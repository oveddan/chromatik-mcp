---
class: apotheneum.mcslee.Raindrops
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Raindrops.java
sourceSha256: babc602b2097e54efc4a0d850be0f2ce9c72f01405b9e21fbeb5e356e3f9cc0b
classBytesSha256: f048811334002050268dd80cd5dc092a0f7103b15aaf87756b7faf2d8604aafe
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, physics, gravity, splash, midi, texture, exterior, trigger
---

## Summary
Falling raindrops on cube or cylinder exterior columns that accelerate under gravity and splash at a floor line; mirrored to the interior every frame.
- Trigger (manual or MIDI note-on) spawns one or more independent drops (Per Trig).
- Cube-vs-cylinder placement, column, and splash row are randomized per drop at spawn.
- Splash ring composites additively; a drop self-removes once its tail and splash fully fade.

## Parameter interactions
- Position (cube/cylinder mix) is SAMPLED once at each drop's spawn — reshapes only future drops, not falling ones.
- Gravity and initial-velocity range act CONTINUOUSLY, integrated into the drop's motion every frame.
- Floor/Rand set the splash row, SAMPLED at spawn; with Link on and a Surfacing pattern on the same channel, cylinder drops instead re-sample the row CONTINUOUSLY from Surfacing's live wave height until they've splashed once. Link has no effect on cube drops.
- Splash only gates whether the ring renders — fall and removal logic run regardless.
- Tail Length sets the trailing streak's fade falloff, read CONTINUOUSLY.

## Usage tips
- Best as an accent/transition layer, not a sole visual — clears to black outside active drops.
- Pair with Surfacing (Link on) for "rain hitting a rising water line" on the cylinder.
- High Rand values scatter splash rows into noise; keep it low for a clean single splash line.
