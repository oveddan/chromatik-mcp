---
class: heronarts.lx.pattern.texture.NoisePattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/texture/NoisePattern.java
sourceSha256: ab0d08bd99b94c16600525dc9f0ab108f505d3ad1e3cd23cf04bf89288bcde95
classBytesSha256: ec9f16d0e40b80d36b258aa248a7da6d0ccb13eab0b3e49efbdaba38ba910044
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: texture, generative, motion, geometric
---

## Summary

NoisePattern generates a grayscale brightness field across the model by evaluating a 3D noise function at each pixel's spatial coordinates, which are animated over time by three independent SawLFO modulators driving an offset through the noise space. The algorithm can be standard Perlin noise, fractal Brownian motion (fBm), ridge noise, turbulence, or pure random static; the multi-octave variants stack successive octaves with configurable lacunarity and gain to produce detail at multiple spatial frequencies. The output level and contrast parameters center and scale the raw noise output before clamping to a min/max brightness range.

## Parameter interactions

Scale is the dominant spatial control — higher values zoom in, producing finer high-frequency texture, while lower values zoom out for large smooth blobs. Per-axis scale and motion parameters let the noise stretch or animate at different rates on each axis independently, so setting only Z-motion causes the field to flow along Z while appearing stationary in X and Y. Coordinate mode per axis can be set to center (symmetric around the midpoint) or radial (distance from center), producing rotationally symmetric noise patterns rather than linear sweeps. For multi-octave algorithms, increasing octave count adds higher-frequency detail; lacunarity and gain then control how rapidly the spatial frequency increases and how the amplitude of each octave diminishes. The invert parameter cross-fades between normal and inverted output, and the rotation controls (yaw, pitch, roll) reorient the entire noise coordinate frame without affecting the animation axes.

## Usage tips

NoisePattern is a go-to texture layer that pairs well with color effects — apply a ColorizeEffect or GradientMaskEffect on top to map the grayscale output to any palette color. For slow atmospheric breathing, use a single octave of Perlin with low Z-motion speed. Ridge noise produces sharp luminous ridgelines that look effective for lightning or vein-like textures. The static algorithm randomizes every pixel independently each frame and has no coherent structure — it is useful only for testing or as a noise source for an effect that integrates over time. Seed the noise function to get consistent but varied starting states across multiple instances.
