---
class: apotheneum.mcslee.CylinderBursts
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/CylinderBursts.java
sourceSha256: 084c26469904527e71481465e3b5a0a6e77cc983000e7f69d9d7a970ffb5a69b
classBytesSha256: 2ac733305899a92b8f54fe63c54a00707a02eb3c4b42576d9bcb1cd70d74cad3
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, midi, trigger, motion, geometric, strobe
---

## Summary
- Trigger-driven pattern (shared "Bursts" mechanics, same base as CubeBursts) that spawns expanding outline shapes on the cylinder's exterior each time it fires, from a manual trigger or MIDI note-on.
- Bursts are computed across the full cylinder exterior as one wrapping surface: horizontal distance wraps around the circumference, so a burst near the seam continues seamlessly onto the opposite edge instead of clipping.
- There is no per-face targeting (the cylinder has no discrete faces) — every burst renders on the single exterior surface; after render, the finished exterior is copied onto the interior.
- Outline shape (circle/square/diamond/cross or a blend), growth curve, ring thickness, attack ramp, and lifetime fade all match the shared Bursts mechanics.

## Parameter interactions
- Per-trigger count, shape blend and its per-burst jitter, spin and its per-burst jitter, and radius/thickness/attack/exponent/time all behave as in the shared Bursts mechanics: spin applies live to every currently-rendering burst each frame, while spin- and shape-randomization are each sampled once per burst at spawn.
- Because the surface wraps, spread's vertical (height) bias behaves as on the cube, but horizontally there is no edge to bias toward or clip against — origins can land anywhere around the circumference, wrapping through the seam freely.

## Usage tips
- Reads as a more continuous, ring-like ripple than the cube version, especially with circular shape blends — good for a unified 360-degree pulse.
- A high per-trigger count with a short burst time gives a dense radial "confetti" texture; a low count with a long time and low thickness gives slow, deliberate expanding rings.
