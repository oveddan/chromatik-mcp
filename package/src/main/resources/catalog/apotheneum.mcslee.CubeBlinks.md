---
class: apotheneum.mcslee.CubeBlinks
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/CubeBlinks.java
sourceSha256: b5a1244124ec35db959e5c9cf1c5cd6fedbc5d740445e523b99fcaae4b743326
classBytesSha256: 0cfd55b58c9fdb718350486435bc85cf7ead80f5b12ffcc906f1f75341e9ace7
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, strobe, motion, geometric, midi, trigger, utility
---

## Summary
- Entirely trigger-driven: idle frames render pure black; every visible effect is a short-lived "blink" spawned by one of many per-algorithm, per-face triggers (front/right/back/left/all faces, across 16 algorithms including 2 "random" pseudo-algorithms).
- Each blink sweeps a shape — static/shrinking block, 4 directional wipes, in/out horizontal or vertical bands, in/out diagonal bands, in/out rings — across one face over its own release time, fading out, then removing itself.
- The random pseudo-algorithms draw only from algorithms whose own "eligible for random" toggle is on: one applies an independently-random algorithm per targeted face, the other applies one shared random algorithm to all targeted faces.
- Blinks blend additively — overlapping ones brighten rather than replace, and can wash to solid white if triggered rapidly at high peak/low contrast.

## Parameter interactions
- Peak brightness and release time are each scaled by MIDI velocity via independent mix controls (0 = no effect, higher = full scaling); manual triggers act as full velocity.
- Release-shape and position-shape exponents are sampled once at spawn and warp, respectively, the fade-out and sweep-position curves — above 1 delays then accelerates the change, below 1 front-loads it.
- Contrast is read continuously (updates live mid-blink) and controls how hard the sweep edge cuts off, independent of algorithm.
- A MIDI note's pitch selects the trigger via modulo over the trigger count; a "MIDI routes to all faces" toggle remaps every note to that algorithm's all-faces trigger.

## Usage tips
- Inert without a MIDI controller or a manual trigger click — built for live percussive triggering, not ambient motion.
- Use the per-algorithm "random eligible" toggles to curate which shapes the random triggers draw from, without disabling an algorithm's own trigger.
