---
class: heronarts.lx.effect.FreezeEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/FreezeEffect.java
sourceSha256: 4d0e04ab74cd59ceebda19d761e6d5fd80ba35b5fce9397f2ded461845be54bc
classBytesSha256: 158dc22db9a8711dd0cd7c06580ec4d5a5c5ad1c1614f838cb4a13e6c3954954
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: utility, motion, strobe
---

## Summary

FreezeEffect captures the current color buffer into a snapshot and composites that snapshot back over the live buffer using a configurable blend mode and an attack/release amplitude envelope. When engaged, the captured frame crossfades in over the attack time; when released, it fades out over the release time, allowing smooth transitions in and out of the freeze state. Three activation paths exist: a latching toggle that stays frozen until disabled, a momentary hold that unfreezes on release, and an internal interval modulator that can sync frame captures to tempo or a periodic clock. A manual resample trigger lets you explicitly capture a new frame at any time without toggling the freeze state.

## Parameter interactions

Attack and release times determine the smoothness of the freeze crossfade — zero attack causes an instant snap, while longer attacks create a slow dissolve into the frozen state; release does the same on exit. The mix level scales the maximum opacity of the frozen frame within the blend. The mode enum selects the compositing operator applied per pixel: Replace (lerp) fully substitutes the frozen image; Multiply darkens the live content by the frozen values; Add, Subtract, Difference, Spotlight, and Highlight all produce creative ghost-overlay interactions. The internal Interval modulator parameters control tempo-synced periodic capture.

## Usage tips

FreezeEffect is most dramatic on fast-changing animated content where the captured frame diverges visibly from the live stream during the hold period. Use momentary Hold with Replace mode for stutter effects triggered by pads or MIDI notes. With Multiply mode and a slow release, the frozen frame acts as a decaying filter on the live content rather than a hard cut. The interval path enables strobed freeze-frame sequences that automatically sample on the beat without any manual triggering.
