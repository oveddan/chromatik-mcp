---
class: apotheneum.core.ApotheneumDoors
kind: effect
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/core/ApotheneumDoors.java
sourceSha256: 0dee3a44882ccc0989c5463075584ac0a0d6dd4b106c31f2513698248b0a0577
classBytesSha256: b7e74fd20d96a0a30960c958f5b77ecac303a69ee4fa8c8ce485ecd61f8741f4
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: masking, geometric, utility
---

## Summary

Blacks out the fixed pixel regions that correspond to Apotheneum's physical doorways, so overlaid content doesn't render onto surfaces that don't physically exist.

- Masks the door-shaped regions on all four cube faces (exterior and interior) and on four fixed positions around the cylinder (exterior and interior).
- The door regions are fixed geometry offsets, not derived from a live model query — this effect assumes the standard Apotheneum door layout.
- Mute is SAMPLED once per frame from a single boolean toggle; when off, no masking is applied and content renders straight through the door areas.

## Parameter interactions

- Only the mute toggle is active; three "glitch" mute toggles exist on the class but are not wired to any parameter registration, so setting them from a client has no visible effect.
- Mute acts as a hard override each frame — it fully replaces pixel colors with black in the door regions rather than blending, so it always wins over upstream color regardless of the effect's enabled amount.

## Usage tips

- Place this effect after any pattern/effect whose output should not spill onto the doorway cutouts, since it unconditionally overwrites those pixels last.
- Because the effect's enabled amount is not consulted, fading this effect's mix/enabled amount down does not fade the masking — it is effectively binary via the mute parameter only.
- Do not rely on the unused glitch-mute parameters to control door glitching; they are currently inert.
