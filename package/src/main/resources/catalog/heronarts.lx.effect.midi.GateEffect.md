---
class: heronarts.lx.effect.midi.GateEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/midi/GateEffect.java
sourceSha256: 51ea6827da04963fc24c2ecb7586a340e3b740c667d3963f2010d86b56a74c7d
classBytesSha256: b70b04cb77ee6abea9eef008a69a123cb3546cd4d5f815ad41d6cd43843315ee
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: midi, audio-reactive, masking, utility, envelope
---

## Summary

GateEffect multiplies the entire color buffer by the output of an AHDSR envelope, gating the brightness of the incoming content in response to MIDI note-on/off events, a manual trigger, or an external trigger parameter. When a note-on arrives the envelope engages and rises from the initial level through attack, hold, and decay phases to a sustain level that is held until note-off triggers the release; the buffer brightness tracks this envelope value each frame via pixel-wise multiply. In one-shot (Trigger) mode the envelope runs through attack, hold, and decay without waiting for a note-off release. MIDI velocity and note pitch can each scale the envelope's peak level up or down, making louder or higher notes produce brighter output.

## Parameter interactions

The initial and peak levels define the brightness floor and ceiling of the envelope — initial sets the level before any note is active, and peak sets the maximum achieved at the end of attack. Delay, attack, hold, decay, and release are time durations scaled in milliseconds with exponential scaling at the control end, so fine resolution at short times is easy to dial in; sustain is a level (a normalized percent, not a time) that sets the brightness the envelope holds after decay completes and before note-off triggers release. The shape parameter bends the envelope's response curves: positive values push attack and decay toward their late portions (slower start, faster finish) while negative values do the inverse. Velocity response and note response each scale the effective peak proportionally to the incoming MIDI data — a positive velocity response makes the peak proportional to how hard the key was struck, while negative response inverts this. The three trigger modes (Retrig, Legato, Reset) determine what happens when a second note arrives while the envelope is already active: Retrig re-triggers from the current level, Legato ignores the new note until all notes are released, and Reset jumps the envelope back to zero before re-engaging.

## Usage tips

GateEffect is the standard mechanism for making channel content appear only when MIDI notes are playing — place it at the end of an effect chain so the shaped, colored, transformed output is gated cleanly on each note hit. For percussive flash-hits, use short attack (near zero), short hold, and moderate decay with zero sustain and release so the content flashes and fades quickly after the note. For sustained instrument-following use full sustain at one with a comfortable attack and release time. Set velocity response to a positive value so quieter notes produce subtler flashes rather than always hitting full brightness. When not using MIDI, the manual trigger and target trigger parameters allow the same AHDSR envelope to be engaged from LX automation or other MCP tool calls.
