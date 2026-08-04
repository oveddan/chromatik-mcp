---
class: heronarts.lx.pattern.PatternRack
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/PatternRack.java
sourceSha256: f7c195036b39c943029f9241112f7484362d4ef72ecd07f6f636636283323d2a
classBytesSha256: e46d377128487b4bfa5679c6242a6d3fa144e436a73f1acbd5ef25ad50d8c302
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.2/lx-1.2.2.jar
lxVersion: 1.2.2
generatedAt: 2026-08-03T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
tags: utility, generative, container
---

## Summary

Embeds a complete pattern engine inside a single pattern slot, giving that slot its own child pattern list, transition engine, and auto-cycle behavior.

- Each frame the rack delegates to its internal engine, which runs (or cross-fades) its active child pattern and writes into the rack's own buffer; the parent channel then treats that buffer as the rack's single pattern output.
- MIDI and OSC messages addressed to the rack are forwarded to the internal engine using the same filtering/addressing a top-level channel uses.

## Parameter interactions

- The rack exposes all pattern engine parameters (transition time, auto-cycle, blend mode) directly on itself, but deliberately excludes them from clip automation and snapshot control so the rack's internal playback state isn't captured or restored by channel-level snapshots.

## Usage tips

- Use when one fixture group needs to auto-cycle independently through its own sub-collection of patterns while the rest of the show does something else.
- Child patterns are addressable via OSC/MIDI at the same relative sub-path the top-level engine uses, so existing controller mappings largely carry over.
- Because rack parameters are snapshot-excluded, don't rely on snapshots to capture or restore which child pattern is currently active inside the rack.
