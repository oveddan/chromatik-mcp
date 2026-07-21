---
class: apotheneum.mcslee.Wormhole
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Wormhole.java
sourceSha256: ea36b36be583bcf99af869ccc0e468574e88d39d88ef270b1511a4b7e7f5c3b4
classBytesSha256: 88f87a57921925c87dd17da6c569b621eb304f06dadc30d8ab0d69b1eceb44b7
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, ring, pulse, midi, exterior, strobe, trigger
---

## Summary
Sends a bright horizontal ring pulse traveling continuously along one fixed journey: cube exterior top-to-bottom, then cylinder exterior rings.
- Each trigger spawns an independent pulse layer; brightness peaks near the current position and fades behind it, additively combined onto the frame.
- A pulse self-removes once past the journey's end; the exterior render is copied onto the interior each frame.

## Parameter interactions
- Speed sets steady travel rate, read CONTINUOUSLY.
- Thresh and Accel let a pulse start slow then speed up: Accel only accumulates once progress exceeds Thresh, so a threshold near the end produces a late acceleration "snap" just before completion.
- Width sets how many rings ahead are lit at full brightness before falloff; Fade sets how fast brightness decays behind it — large Fade gives a long comet trail, small Fade a tight pulse (both CONTINUOUS).
- Pulses are additive layers — triggering several in quick succession stacks brightness where they overlap and can blow out to white.

## Usage tips
- Good for a rhythmic pulse effect from a MIDI pad — rapid retriggering produces a train of pulses, not a restarted single one.
- The journey path is fixed and not reconfigurable; reshape it via Speed/Accel per trigger, not a direction parameter.
- Pairs well as an accent over a static or slow base pattern, since it clears to black and composites only pulse layers.
