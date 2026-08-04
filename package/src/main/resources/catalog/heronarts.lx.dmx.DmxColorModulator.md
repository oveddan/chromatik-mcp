---
class: heronarts.lx.dmx.DmxColorModulator
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/dmx/DmxColorModulator.java
sourceSha256: f46178f97cac851f164908b8a291a14641530dee80c3f25c1796c0feaaa52c95
classBytesSha256: 4a525c7f68eeaef6e04a7ed47bab5bb75937f04e742b587e611a35a7f8633409
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.2/lx-1.2.2.jar
lxVersion: 1.2.2
generatedAt: 2026-08-03T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
tags: dmx, color, utility
---

## Summary

DmxColorModulator reads three consecutive DMX channels as an RGB triplet in a configurable byte order and exposes the result as both its computed value and a dedicated color parameter, optionally pushing that color live into a slot of the global palette's active swatch.

- Not a trigger source and has no threshold/gate behavior — reads 3 bytes every frame.
- It opts out of being a live-mapping source in the UI at construction time; it's meant to be read directly via its color parameter, not patched as a modulation input.

## Parameter interactions

- The palette-update toggle, when on, acts CONTINUOUSLY: every frame it writes the live DMX color into the configured swatch slot in the active swatch, growing the swatch if the target index doesn't exist yet.
- The fixed-swatch-mode toggle, when on alongside the palette-update toggle, forces that swatch color's mode to FIXED on every update, continuously overriding any transition/cycling configured on that slot.

## Usage tips

- Use this to bridge a DMX console's color channels directly into the LX palette system rather than manually polling DMX in a pattern.
- Disable the palette-update toggle before manually editing that palette slot in the UI, or edits get clobbered on the next frame.
