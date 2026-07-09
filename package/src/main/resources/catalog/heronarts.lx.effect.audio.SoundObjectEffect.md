---
class: heronarts.lx.effect.audio.SoundObjectEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/audio/SoundObjectEffect.java
sourceSha256: c20b3fd26909eb9e302719a94bbab19ddcb2c07e1562bab15d3508f9e2ea28c4
classBytesSha256: 907d7c214a9e5d43973ce895bc674bd78a55db7d2af7ccf4aac90c0e72bb74f1
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: audio-reactive, masking, spatial, utility
---

## Summary

SoundObjectEffect applies a spatially-positioned audio mask onto the incoming color buffer each frame by delegating to the same engine used by SoundObjectPattern. The engine generates a per-point brightness field centered on a virtual sound object whose size and luminosity are driven by an audio signal and optional modulation input; that field is then composited against the existing buffer using one of several blend functions (multiply, spotlight, highlight, add, or lerp). A CUE mode bypasses the blend and replaces the buffer outright with the raw mask, useful for tweaking placement before going live.

## Parameter interactions

The base size and base brightness establish a floor for the sound object when the signal is silent; signal-to-size and signal-to-brightness scale how aggressively the audio drives those dimensions upward. A separate modulation input can independently push size and brightness via its own scaling knobs, layering LFO or envelope influence on top of the audio signal. Shape controls (shapeMode1, shapeMode2, and shapeLerp) determine whether the falloff is spherical (orb), box-shaped (Chebyshev distance), axis-aligned slab, or a blend of any two, and positionMode selects which coordinate the object tracks. Mask depth scales the overall blend alpha so the effect can be dialed in gradually, while the mask mode enum determines whether the sound object illuminates, attenuates, or additively brightens the underlying content.

## Usage tips

Use this effect on a channel that already has content rendered — it sculpts what is there rather than generating its own colors. Multiply mode darkens everything outside the sound object radius and is the most dramatic; spotlight and highlight preserve more of the background content while still emphasizing the object's footprint. Pair with an audio modulation source on the signal input for reactive pulsing. The scope parameters let you tune temporal smoothing of the signal, which prevents jitter on fast transients.
