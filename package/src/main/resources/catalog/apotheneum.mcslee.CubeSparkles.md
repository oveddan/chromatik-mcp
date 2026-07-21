---
class: apotheneum.mcslee.CubeSparkles
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/CubeSparkles.java
sourceSha256: ca0f36b36356e883c1f840bbdd31406cde6c35405d6439b4a51af87bbe45beba
classBytesSha256: fa69330250975f3aa554d0ba329865b1f773dd12ef55949fca13f61ee097f1e4
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: audio-reactive, midi, trigger, texture, motion, strobe
---

## Summary
- Trigger-driven pattern (MIDI note-on or manual trigger) that spawns short-lived vertical sparkle flares, each confined to one randomly chosen column, optionally on the cube and/or cylinder exterior via two independent enable toggles.
- Each sparkle picks a random column and a random base height (capped by a max-height fraction); over its life the glow's peak creeps outward on a power curve while its width auto-widens on a fixed trajectory (not a parameter); brightness fades from full to zero.
- A shape control selects whether the glow spreads above and below the base position, only upward, or only downward.
- Sparkles blend additively and render on black; each frame the finished cube/cylinder exteriors are copied to their interiors.

## Parameter interactions
- Per-trigger count is uneven across components: with cube enabled, each trigger spawns that many sparkles on *every* cube face (4x total), while cylinder spawns exactly that many total.
- Max-height caps how far up the column a base position can land, confining sparkles to a lower band at small values or the full column at larger ones.
- The distance-growth exponent shapes only the timing of the glow's creep; width growth is fixed and independent of it.
- Shape changes only which vertical side lights up, independent of timing controls.

## Usage tips
- Enable only cube or only cylinder to isolate sparkles to one component for independent MIDI-driven textures; enable both for correlated sparkles from the same trigger.
- Cube spawns 4x the per-trigger count (once per face) — keep count low when driving both together.
- Good generic texture layer over a base pattern via blend mode — renders on black, leaving untouched columns dark.
