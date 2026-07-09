---
class: heronarts.lx.pattern.strip.ChasePattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/strip/ChasePattern.java
sourceSha256: 3ee0f6341098947eab36ab1cf9f82f54419e23b6fe2779816b27d16d7c2015a8
classBytesSha256: 4b5cfb80bd3a519467aa3340927a17426ba7e230a53365b154e0ebe232f216da
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: motion, strip, geometric, generative
---

## Summary

ChasePattern produces a pixel-index-based chase effect that divides the model's point list into repeating chunks and animates a bright region within each chunk using a selectable waveshape. Each frame, the pattern evaluates a waveshape (sine, triangle, sawtooth up/down) of the time basis, maps its output to a position within the chunk, and computes a distance from that position to each pixel's index within its chunk; pixels close to the moving position are lit brightly and the falloff is determined by a configurable size and fade envelope. The motion can run at a free speed in Hz or lock to a tempo division.

## Parameter interactions

Chunk size, min/max chunk bounds, and the global shift amount together define the spatial structure of the chase: larger chunks make fewer, wider segments; the shift parameter offsets each successive chunk's motion phase so chases do not all fire in lockstep. The waveshape and its skew and exp modifiers reshape the position curve before it maps to the chunk — skew biases the peak toward one end while exp sharpens or softens the pulse. Wrap mode changes how the distance from the moving peak to a pixel is computed: ABS gives a symmetric chase that is equidistant on both sides, POS/NEG give asymmetric leading-edge or trailing-edge chases, and CLIP variants eliminate wrapping entirely. The optional swarm modifier uses a 2D XY attractor to suppress or amplify brightness and edge softness in one region of the model, creating a spatial clustering effect that the base chase does not have.

## Usage tips

ChasePattern is best suited to strip-topology models where pixels have a natural sequential order along a physical run. For 3D models the effect depends on the LXModel point iteration order, which may not match the spatial layout. Modulate the speed or shift parameters from an LFO to build more complex animated chases, or use shift to create a staggered canon effect across many parallel strips. Tempo sync mode is useful for music-driven shows; set the division to match the beat structure and the chase automatically stays phase-aligned to the BPM.
