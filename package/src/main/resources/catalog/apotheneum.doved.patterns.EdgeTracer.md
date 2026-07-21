---
class: apotheneum.doved.patterns.EdgeTracer
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/doved/patterns/EdgeTracer.java
sourceSha256: 9bbb7750ee319c798a8eeda24b18645a5b4380409e1875d9324ad750905e0c9e
classBytesSha256: 8ff3ba56eb2f9b327630449a60e9aad4c0152fa625cef287e8dbf270df5c5a66
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, geometric, edge, chase, exterior, interior
---

## Summary
Traces a moving lit segment along a precomputed path of real 3D points following a physical edge or perimeter, built once at construction for cylinder and cube, exterior and interior.
- Path choices: bottom edge, top edge, per-face perimeter, or a flattened front-door-height line; bottom-edge and per-face paths detour around door openings.
- Drawn by additively coloring every LED within a 3D distance radius of any lit path point — real spatial width, not path-index width.

## Parameter interactions
- Surface and, for cube, Cube Mode select which path(s) render; switching either does not reset Position, so the trace continues from the same normalized position on the new path.
- Position sets the trail's leading edge along the path (CONTINUOUS); the trail clips (doesn't wrap) at the path start, so values near the start give a short or absent trail.
- Length sets how many path points feed the trail; Width sets the search radius per point against every model point — large Length and Width together are costliest.
- "Both" and "Per Face" modes draw multiple independent segments simultaneously, all sharing one Position, so they move in lockstep.

## Usage tips
- Because the path follows real geometry including door detours, it reads as an architectural outline reveal rather than an abstract sweep.
- Keep Width modest for performance; very large Width can bleed the trace across geometrically close but path-unconnected faces (e.g. near corners).
