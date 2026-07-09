---
class: heronarts.lx.effect.InvertEffect
kind: effect
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/effect/InvertEffect.java
sourceSha256: 6510739084044dfccd1c9c03e6243bed9784840ce90e4cef7b2f74cea46f21fd
classBytesSha256: 2295b7320f3f2268ef6c7c21b06f55423c9b84658d51527c16f1c64b1a269c16
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: color, utility, invert
---

## Summary

InvertEffect subtracts each pixel's RGB channel values from 255 via separate per-channel lookup tables, producing a photographic negative of the incoming color buffer. The overall amount parameter scales how far each channel is interpolated from its original value toward its fully inverted value, and each channel has an independent amount multiplier so the inversion can be applied selectively across red, green, and blue. Alpha is preserved untouched. Lookup tables are rebuilt lazily whenever any amount changes, making the per-frame pixel loop a simple three-table lookup per pixel.

## Parameter interactions

The master amount scales all three channel amounts equally before they are multiplied by their per-channel factors, so reducing the master to half-way partially inverts all channels together while individual channel amounts let you apply full inversion to one channel and zero inversion to another. Setting the master to full and then zeroing individual channels achieves selective inversion: for instance, inverting only red leaves green and blue intact, shifting warm colors toward cyan. All three amounts at zero makes the effect a no-op and the loop exits early, so there is no render cost when fully bypassed.

## Usage tips

Use InvertEffect to produce complementary color relationships from a pattern — running it after a warm amber pattern yields cool blue-teal without changing the underlying pattern. Animating the master amount creates a smooth negative-to-positive oscillation, effective when driven by an LFO for a psychedelic cycling look. Setting per-channel amounts to different values is a lightweight color grading tool: full red inversion with green and blue unchanged shifts the entire palette toward cyan and creates a split-toning effect. Stack it after DynamicsEffect to invert a shaped brightness curve rather than a flat linear response.
