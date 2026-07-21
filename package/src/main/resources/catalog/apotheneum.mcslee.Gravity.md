---
class: apotheneum.mcslee.Gravity
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Gravity.java
sourceSha256: f72d94998d3c3759938de989780c77cb4a1c3c0a5676e0ceeccc424784254953
classBytesSha256: 0d6b88b79c6cdaa298ed2c428fe167f5b2626b4059750f07172ea66652371732
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, physics, geometric, cube, cylinder, osc
---

## Summary
- Simulates 30 glowing "orb" objects under simple physics: 20 confined to the cube's four faces (5 per face, bouncing off each face's left/right edges) and 10 free on the full cylinder (wrapping, no lateral bounce).
- Vertical motion is gravity + friction with bounce/punch impulses at floor/ceiling; lateral motion is push (per-orb fixed direction) + brake. Orbs render as soft radial blobs, lightest-color blended.
- Cube and cylinder populations toggle on/off independently without restarting the pattern.

## Parameter interactions
- Gravity strength/direction (bipolar — negative "falls" upward) and friction (opposes vertical motion, plus per-orb variance) are CONTINUOUS; higher friction damps oscillation over time.
- Bounce scales rebound velocity at floor/ceiling (below 1 damps, above 1 amplifies); punch adds a fixed kick away from the surface.
- Push accelerates each orb sideways in a fixed per-orb random direction; brake decelerates lateral velocity — both apply to cube and cylinder alike, differing only in boundary behavior (bounce vs. wrap).
- Min/max radius set each orb's fixed size, SAMPLED once at spawn; shrink reduces radius live near the top of its range.
- High-velocity bounce/wall events above a threshold optionally fire rate-limited (~60ms) OSC triggers to a local Ableton transmitter when output is enabled.

## Usage tips
- Low gravity with high bounce/punch gives persistent bouncy orbs; high friction with low bounce settles into a resting drift.
- Push/brake drive horizontal motion — cube faces bounce between edges, the cylinder drifts/spins continuously.
- OSC output only matters with a live local Ableton listener.
