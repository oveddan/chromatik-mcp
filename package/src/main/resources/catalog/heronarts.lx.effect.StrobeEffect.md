---
class: heronarts.lx.effect.StrobeEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/StrobeEffect.java
sourceSha256: 71bbb2df85e026ff0406e2a6524f4701ad079a277b2447f4cdaf3fe11d9afa9a
classBytesSha256: cc63debe9b4f5a7d65ff26cf2a24efdc2f6ac2ec7e823272ec5fe1c2c39628b0
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: strobe, motion, utility, color
---

## Summary

StrobeEffect multiplies the entire color buffer by a periodic brightness envelope derived from a running SawLFO oscillator, effectively pulsing all pixels in the output between full brightness and black at a configurable rate and waveform shape. Each frame the oscillator basis is fed through the selected waveshape (sine, triangle, square, ramp up, ramp down) and then warped by an exponential bias that skews the waveform toward the high or low end of its range. The resulting 0–1 level is lerped from 1 (full) toward the waveform output according to the depth parameter, so depth at zero leaves the buffer unchanged regardless of oscillation, and depth at full produces the deepest possible blackout valleys. When tempo sync is enabled the basis is derived from the global tempo clock at the selected division with an optional phase offset rather than the internal SawLFO.

## Parameter interactions

Speed sweeps the oscillation rate between the configured minimum and maximum frequency bounds with a squared exponent so that the lower end of the range is more finely controllable. Depth sets how far the buffer drops during the dark portion of the cycle — at low depth the buffer only dims slightly while at full depth it reaches full blackout. Bias shifts the waveform asymmetrically: a positive bias compresses the bright portion into a short spike and extends the dark period, while negative bias does the opposite, producing long bright holds with brief dark flashes. The waveshape selection determines the transition profile: square produces hard on/off cuts while sine and triangle produce smooth fades, and the ramp shapes create sawtooth strobes that ramp in or out. Tempo sync locks the cycle phase to the global clock so the strobe hits on beat divisions.

## Usage tips

StrobeEffect is most impactful when depth is at or near full with a square waveshape, producing hard blackout cuts at the strobe rate — this is the classic performance strobe. For atmospheric use, switch to sine or triangle with depth around 50% to create rhythmic dimming pulses rather than hard cuts. Sync to tempo at a quarter- or eighth-note division for beats-aligned pulsing that complements musical content. Stacking two StrobeEffect instances at different frequencies can create complex beating patterns. Be aware that very high frequencies with square waveshape at full depth can produce photosensitive hazards; use bias to shorten duty cycle or reduce depth when deploying in public settings.
