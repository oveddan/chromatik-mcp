---
class: heronarts.lx.effect.StrobeEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/StrobeEffect.java
sourceSha256: 71bbb2df85e026ff0406e2a6524f4701ad079a277b2447f4cdaf3fe11d9afa9a
classBytesSha256: 8a0f9bac6336c1ab53ebe650c16ee3c30f02791e781b6bfa1177dd57d40d037f
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.2/lx-1.2.2.jar
lxVersion: 1.2.2
generatedAt: 2026-08-03T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
tags: strobe, motion, utility
---

## Summary

Multiplies the whole buffer by a periodic brightness envelope driven by an internal saw oscillator or, when synced, the global tempo clock.

- Depth sets how far brightness drops during the dark part of the cycle; depth at zero leaves the buffer unchanged regardless of waveshape or speed.
- Bias exponentially skews the waveform CONTINUOUSLY: positive compresses the bright portion into a short spike (long dark hold), negative does the opposite.
- Speed sweeps oscillation rate between a min/max frequency bound with a squared response curve, finer at the low end.
- Waveshape (sine/triangle/square/ramp up/down) sets the transition profile — square gives hard cuts, sine/triangle give smooth fades.

## Parameter interactions

- Tempo sync replaces the free-running oscillator with the global tempo clock at a chosen division plus phase offset, locking the cycle to beat divisions instead of a fixed Hz rate.
- Bias and waveshape combine: square + strong positive bias gives the hardest blackout spikes; sine/triangle + near-zero bias gives smooth rhythmic dimming.

## Usage tips

- Depth near full + square waveshape is the classic hard-cut performance strobe.
- Depth around 50% + sine/triangle gives atmospheric pulsing rather than blackout.
- Sync to a quarter- or eighth-note division for beat-aligned pulsing.
- High frequency + square + full depth is a photosensitivity hazard in public settings — reduce depth or use bias to shorten the dark duty cycle instead.
