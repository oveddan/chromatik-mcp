---
class: apotheneum.mcslee.Surfacing
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Surfacing.java
sourceSha256: 520a0ff2e6c323c2939a4b05614e655934b333a02b8617856e1968ab38073246
classBytesSha256: 6a748803f1a6fc77abe24ae4e54e95f0f1e1e1871db0fe0680b364d0f3694151
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, motion, wave, texture, exterior, transform
---

## Summary
Renders a soft-edged horizontal fill boundary across every column of the cube and cylinder exteriors, as if partially submerged in an undulating liquid.
- Per-column height sums up to three configured sine waves, evaluated after a shared yaw/roll rotation, compared against every point via a selectable fill function.
- Exterior renders copy onto interiors unmodified — no independent interior control.

## Parameter interactions
- Top-level Yaw/Roll rotate the frame shared by all waves CONTINUOUSLY; each wave's own Yaw rotates only its own contribution, letting waves run diagonally for interference-like patterns.
- Amplitude, Center, Wavelength, Phase shape each wave's sine term CONTINUOUSLY; Phase animates naturally under LFO modulation.
- Size and Fade jointly set the fill band's thickness/softness — Fade divides both Size and the distance computation, so small Fade sharpens the edge, large Fade smears it into a gradient.
- Fill mode changes whether the lit region hugs the surface line, fills below it, or above it.
- Level is a global dimmer; 0 short-circuits the render. Cube/cylinder toggle off independently.

## Usage tips
- Works well as the base layer under Raindrops (Link Floor on) — Surfacing exposes its live per-column height for Raindrops to splash against, on the cylinder only.
- Zeroing a wave's Amplitude is a cheap way to run with fewer active waves.
- Large-wavelength low-amplitude + short-wavelength low-amplitude waves together produce a choppy-water texture.
