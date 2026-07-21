---
class: apotheneum.doved.patterns.Ants
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/Ants.java
sourceSha256: caf7ebe5a6ad68f89fa8139b8d927cf0817ef4c08db863cad924d66c133dbcb0
classBytesSha256: 8060ef2864836cf679eaf01f04d114c891fb36e566589d6d78b414d551f857b4
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, pathfinding, texture, geometric, wander
---

## Summary
Simulates ant-colony path discovery and traffic on the cube or cylinder ring surface (one shape at a time).
- A single seeker wanders briefly, curves toward a target while avoiding door rows, then returns home; its forward leg becomes the one discovered path.
- Moving ants then travel back and forth along that path (interpolated by arc-length) as filled circular blobs, with optional lane offset and small wandering; a completed round trip schedules a delayed re-spawn.

## Parameter interactions
- Start/Target feed the seeker's pathfinding, but changing either while a path exists clears all ants and forces full re-discovery — scrubbing these live is a visible reset, not a smooth retarget.
- Explorers governs how often a new ant seeds a second/third discovered path instead of following the existing one, capped at three total.
- Quantity and the round-trip delayed-spawn mechanism jointly set population growth, capped at Max Ants.
- Lane Sep must be on for Lane Dist to have any effect (outbound/return ants offset into separate lanes).
- Forward/Return render targets (exterior, interior, both) are independent per direction.

## Usage tips
- Expect a startup delay before moving ants appear — the seeker must finish a full wander-seek-return cycle first, a poor fit for instant-payoff effects.
- Treat Start/Target as scene-setup controls, not live knobs, since changing them resets the simulation; use Speed/Quantity/wander for live tweaking.
- Shape switches the entire coordinate system at once — ants in flight aren't carried over between cube and cylinder.
