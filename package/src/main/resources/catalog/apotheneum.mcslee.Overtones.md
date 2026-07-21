---
class: apotheneum.mcslee.Overtones
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Overtones.java
sourceSha256: dd3da7798bd5fa77e80022253f069914c96618781f39c6a1ced9142ff37eb19c
classBytesSha256: fca1c16836441d5b39929ed477a102626e5e8b3452943e7d781a908df330d156
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, geometric, cylinder, osc
---

## Summary
- Renders 30 vertically-oscillating horizontal bands ("tones") across the cylinder's 120 exterior columns (cube untouched), then copies to the cylinder interior.
- Each tone's vertical position is the sine of its phase; phase per tone is the base phase plus that tone's index times the offset, scaled across a full 360-degree cycle, both read directly from live parameters each frame — neither auto-advances internally, so motion requires an external modulator driving base or offset.
- The 120 columns split into blocks of 30; even blocks map to tones forward, odd blocks reversed, so adjacent blocks' bands move in opposite directions and meet/part at block boundaries.

## Parameter interactions
- Base phase-shifts the whole system; offset sets phase spacing between successive tones — at 0 all move in lockstep as one band, larger values fan them into a wave. Both CONTINUOUS.
- Amplitude scales sine swing vertically (0 collapses all tones to the cylinder's vertical center).
- Width sets each band's falloff thickness (thin sharp line vs. soft wide glow).
- When output triggers are enabled, every tone's phase is checked each frame for crossing 90°(peak) or 270°(floor); any single tone crossing is enough to fire the corresponding OSC message to a local Ableton-bound transmitter.

## Usage tips
- Wire a modulator (e.g. LFO) to base or offset — otherwise the pattern renders a static snapshot every frame.
- Small offset with slow base motion gives one undulating band; larger offset creates a standing-wave-like fan.
- Cylinder-only — pair with a separate cube pattern for a full-building look.
- OSC output only matters with a live local Ableton listener.
