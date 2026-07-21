---
class: apotheneum.mcslee.DNALetters
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/DNALetters.java
sourceSha256: db6c252cd8ea15a7a24687e64e32e3a266a86b37735d2f6d324868e8a62b2bf9
classBytesSha256: 12a51e5d30a0b588be705dfff83dbb7de748e7300973140c6f99dea29936e318
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, texture, midi, cube, trigger, utility
---

## Summary
- Tiles a 5x5-pixel-font grid of DNA base letters (A, C, T, G) across the cube front face in grayscale, then mirrors that face onto the other three cube faces (and the interior, via copy), so all four faces always show identical content.
- Cells are black by default; each activation fades in over ~500ms once triggered and stays lit until an explicit reset — the pattern makes no continuous per-frame changes on its own.

## Parameter interactions
- A reset trigger clears all active cells to black; an update trigger activates a batch of cells (count set by a discrete parameter), each reassigned to a new letter — activation and letter choice are SAMPLED once per trigger, not continuous.
- Activation is sticky, so repeated updates accumulate lit letters until reset; letter density grows unbounded without one.
- MIDI note-on drives the same two actions: pitch 0 triggers reset, any other pitch triggers an update.

## Usage tips
- Use for a percussive, MIDI- or sequencer-cued reveal rather than ambient motion — it is inert between triggers.
- Pair with a periodic reset (LFO/sequencer/manual) in long-running shows, or the grid saturates solid.
- All four cube faces are always identical; there is no per-face independence.
