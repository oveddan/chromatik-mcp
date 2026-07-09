---
class: heronarts.lx.pattern.PatternRack
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/PatternRack.java
sourceSha256: 7528b63971035d14e97e89242fb82373d085a212f105ae03483b1023eb47d849
classBytesSha256: 03dce456745b0d6fefec29a9b2367d58d9fa739c25f705fe3f1da4b5f693a5e0
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: utility, generative
---

## Summary

PatternRack is a container pattern that embeds a full LXPatternEngine inside a single pattern slot, giving it its own internal list of patterns, transition engine, and auto-cycle behavior. Each frame the rack delegates rendering to its internal engine, which runs the active child pattern (or a cross-fade between two) and writes the result into the rack's own buffer, which the parent channel then consumes as the rack's pattern output. This enables nested pattern management: a single channel can host a rack, and the rack independently cycles through its own pattern list just as the top-level channel would.

## Parameter interactions

The rack exposes all LXPatternEngine parameters — transition time, auto-cycle, blend mode — directly on itself, but deliberately excludes those engine parameters from clip automation and snapshot control so that the rack's internal scheduling state does not interfere with channel-level snapshots. MIDI input is forwarded to the internal engine via the rack's own MIDI filter, and OSC is similarly delegated, so the rack responds to the same addressing conventions as a top-level channel.

## Usage tips

Use PatternRack when a single physical fixture group needs to independently cycle through a sub-collection of patterns while other channels are doing something else — for instance, keeping a background pattern looping on one zone while the main channel transitions freely. The rack's child patterns are addressable via OSC at the same sub-path the engine normally uses, so existing OSC controllers should work with minimal reconfiguration.
