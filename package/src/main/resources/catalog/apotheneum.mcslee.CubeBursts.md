---
class: apotheneum.mcslee.CubeBursts
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/CubeBursts.java
sourceSha256: e173270090396d383cb90c2a7762e76f6f2115c6c7a8780bb0237cf660934eed
classBytesSha256: 186b156047deb92f69aaa088fa36d80fed04ceea47664391b54c0ec6b4b1e4f2
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, midi, trigger, motion, geometric, strobe
---

## Summary
- MIDI/trigger-driven pattern (shared "Bursts" mechanics) that spawns one or more expanding outline shapes on the cube's exterior each time it fires, from a manual trigger or MIDI note-on.
- Each burst grows an outline (circle/square/diamond/cross, or a blend of two) outward from a randomized start point on a single face; radius grows on a power curve, ring thickness ramps in during an attack phase, and brightness fades to zero over the burst's lifetime.
- Bursts do not wrap at face edges — one spawned near an edge is visibly clipped, not continued onto the adjacent face.
- After render, the finished exterior faces are copied onto the cube interior faces.

## Parameter interactions
- Per-trigger count spawns that many independently-randomized bursts (start position, shape-blend jitter, spin jitter) per firing.
- "All faces" alone puts independently-randomized bursts on all 4 faces per spawn; "all faces" + "symmetry" instead clones one randomized burst's parameters across all 4 faces for a matched, rotationally-symmetric result.
- Spin is applied live to every currently-rendering burst each frame — sweeping it live rotates existing bursts continuously — while spin-randomization and shape-randomization jitter are each sampled once per burst at spawn.
- Spread governs how far from face-center burst origins randomize: at/below its unit range origins cluster centrally; above it, origins push toward edges/corners.

## Usage tips
- Burst brightness is not scaled by MIDI velocity here (unlike CubeBlinks), so hits read as uniform-strength flashes regardless of velocity.
- Use "all faces + symmetry" for a mirrored, cube-wide burst; "all faces" alone for a busier, decorrelated look across faces.
- Push spread into the edge-biased range for a deliberate clipped-corner look, or keep it low for clean centered rings.
