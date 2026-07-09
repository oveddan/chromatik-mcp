---
class: heronarts.lx.pattern.audio.SoundObjectPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/audio/SoundObjectPattern.java
sourceSha256: a3bebd665bf96c43ad46be8b04bdef852267205e2627ecc6d5c25b58ed3b0292
classBytesSha256: 4ea4e4589a86a9fdaf1a0bc041bf8a0a7a851ebb7424805c09ec5c2a1222b1c6
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: audio-reactive, geometric, motion, generative
---

## Summary

SoundObjectPattern renders a grayscale orb (or other shape) centered at the spatial position of a tracked sound object, with brightness and size that respond dynamically to the object's audio signal level. The distance from each model point to the sound object's normalized 3D position is computed using a selectable shape function — sphere, box, or axis-aligned slab — and the result is mapped through a soft-edged falloff to produce a bright core that fades toward a configurable contrast boundary. A scope mode additionally modulates per-pixel brightness based on a rolling history of the signal level, so pixels farther from the center reflect older audio history, creating a spatial echo of the sound envelope.

## Parameter interactions

Base size and base brightness establish the orb's resting appearance when no audio signal is present; the signal-to-size and signal-to-level parameters then scale those base values by the live signal, making the orb pulse and expand with louder audio. The manual modulation input provides a second independent scaling path for both size and brightness, useful when automating from a non-audio source. Blending between two shape modes via the shape lerp parameter morphs the geometry continuously from one distance function to the other, allowing hybrid shapes such as a sphere-box cross. The scope amount and scope time work together: higher time stretches the historical trail spatially outward, while higher amount increases how strongly that history dimples brightness near the orb's edge.

## Usage tips

This pattern is most compelling for spatial audio tracking in multi-speaker or ambisonic environments where sound objects move through 3D space and the lighting should follow them. For static mono sources, position mode and the manual modulation input become the primary animation tools. Keep contrast low for a soft diffuse orb and push it high for a sharp ring effect. The scope feature works best at medium speeds — very short scope times produce a flicker at the orb's edge, while very long times create a faint echo halo that outlasts the original transient.
