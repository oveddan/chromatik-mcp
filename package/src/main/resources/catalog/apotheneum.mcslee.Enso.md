---
class: apotheneum.mcslee.Enso
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Enso.java
sourceSha256: 31a74b2c32cfa6a90ad328660bcc2b7e6c6902dfa9c4c5ca6bc394bd2b0a45e0
classBytesSha256: d331947bab206301b7405c58611794799b44dd4f5d3bc1ce2eb63cda0ed08179
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, texture, midi, cube
---

## Summary
- Draws a distorted ring ("ensō" brushstroke) on each of the four cube exterior faces independently, from a per-point radial falloff around a target radius, then copies to the interior.
- The ring can be perturbed by simplex-noise radial deformation and by separate horizontal/vertical "strip" deformations localized to a position band on each axis; both driven by drifting noise, so distortion crawls once speed is nonzero.
- Each face has independent duplicate (2x) and triplicate (3x) toggles whose factors multiply, tiling the ring into 2, 3, or 6 repeats on that face.

## Parameter interactions
- Radius and width set the ring's position/thickness; contrast raises brightness and falloff steepness together, so the same width looks like a soft glow or a hard edge.
- Noise amount (CONTINUOUS) scales overall radial distortion strength; at zero it disables the effect regardless of scale/speed.
- The two strip-deformation pairs (amount + position + width, one per axis) each carve a localized bulge band; amount at zero disables that axis. A shared strip-speed drives both axes' noise together.
- Per-face dup/trip toggles are also controllable via MIDI note on/off (mapped per face), so faces can show different repeat counts simultaneously.

## Usage tips
- Low noise/strip amounts give a clean static ring; nonzero noise or strip speed makes it breathe organically.
- Use per-face dup/trip toggles to break symmetry across faces.
- Only touches the cube — pair with a separate cylinder pattern for a full-building treatment.
