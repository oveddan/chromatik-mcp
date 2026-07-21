---
class: apotheneum.mcslee.Portals
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Portals.java
sourceSha256: 491bdbf1f4f73a54107e8e40c23b8cb058cb2e1ddc6b093baf3cd6817ef8a55c
classBytesSha256: ed81be08b839b71d45444728fb04f55eb63e3c0d7362b6d36e58eb1b340186a7
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, motion, cube, cylinder
---

## Summary
- Renders concentric-stripe "portal" rings emanating from each doorway, computed per column across both cube and cylinder exteriors, then copied to both interiors.
- Per point, a horizontal distance-from-door (clamped to zero within a door-width band) and a vertical distance-from-strip-bottom are combined into one scalar distance, then wrapped into repeating stripe bands.
- Distance is anchored to fixed door positions; nothing advances with frame time internally — motion requires externally modulating a parameter (e.g. distance) with an LFO.

## Parameter interactions
- Avg/max blends the two distance metrics (CONTINUOUS): pure average gives rounder, diagonal-blended contours; pure max gives squared-off rectangular rings that echo the door's shape.
- Distance phase-shifts which stripe band is centered at the door, wrapping through the range — the parameter to modulate for ripple motion.
- Range sets spacing between concentric bands; sharpness biases falloff steepness relative to range (same range can look soft or crisp); contrast scales overall brightness/falloff independent of range.
- An aspect-ratio control scales vertical distance relative to horizontal before combining, stretching/compressing rings vertically without moving the door-relative horizontal center.

## Usage tips
- Modulate distance externally (LFO) for rings that appear to travel outward from the doors; static values render a fixed ripple frame.
- Favor max for an architectural, rectilinear look that echoes the door geometry; average for softer curved contours.
- Touches both cube and cylinder uniformly — a good whole-building "pulse from the doors" treatment without combining separate cube/cylinder patterns.
