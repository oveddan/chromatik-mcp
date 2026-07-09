---
class: heronarts.lx.effect.BlurEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/BlurEffect.java
sourceSha256: 36e0c1c8808c99725aef339cebea0fed7e70028c97f30ec5c6b9d5387a53c3f4
classBytesSha256: c137f8a3a9f1584c0d6fd795768d128d45140a3c9c1d880f8bedd3502069873b
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: motion, texture, utility
---

## Summary

BlurEffect accumulates a temporal motion trail by maintaining a persistent per-pixel blur buffer across frames. Each frame the blur buffer decays exponentially toward black — the decay time sets how long until the buffer reaches the decay factor level (which equals a true half-life only when the factor is 50%), then updates by taking the lightest value between the decayed buffer and the current frame — this ensures the blur cannot accumulate brightness beyond what is present in the live content. The resulting blur buffer is then composited back into the color output using one of five blend modes, producing effects that range from smooth comet trails (Mix or Screen) to luminous afterglow (Add) or darkening smears (Multiply).

## Parameter interactions

Level controls the alpha weight of the blur composite; at zero the blur buffer still accumulates but nothing is written to the output. Decay (in seconds) and Factor jointly define the exponential tail: decay is the time to reach the factor level, so a short decay with a low factor produces tight snappy trails while a long decay with a high factor makes the trail linger brightly for an extended period. The blend mode determines how the accumulated blur interacts with the live frame — Mix (lerp) replaces; Add brightens; Screen caps at white without blowing out; Multiply darkens overlap regions; Lightest preserves the brightest pixel from either source.

## Usage tips

BlurEffect adds temporal smearing and is most effective behind fast-moving point patterns where the trail context matters. At low level values it softens the output without washing it out. Avoid Add mode at high level on bright full-field content — it will saturate to white quickly. The buffer resets to black when the effect is enabled, so toggling it on mid-performance will briefly show a cold-start artifact before the trail stabilizes.
