---
class: heronarts.lx.modulator.ComparatorModulator
kind: modulator
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/modulator/ComparatorModulator.java
sourceSha256: fa87d2a9cc8ab9a56b6b21c62955a05deb1e6e1c8275672593b97648312ae863
classBytesSha256: 67113a33c14bba56ab15ec28064298c02b734fe0f2c3bfb92778468e979b066f
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.2/lx-1.2.2.jar
lxVersion: 1.2.2
generatedAt: 2026-08-03T00:00:00Z
generator: chromatik-mcp-catalog/2 (claude-sonnet-5)
tags: trigger, utility, logic
---

## Summary

ComparatorModulator continuously compares two normalized inputs using a selectable comparison operator (greater/less, or-equal variants, equality, inequality) and exposes the boolean result on its output, which also serves as its trigger source.

## Parameter interactions

- Both inputs and the comparison mode act CONTINUOUSLY — there is no debounce, hysteresis, or edge-only firing; the output simply tracks the live comparison result each frame.
- The equality and inequality modes compare floating-point values exactly, so continuously-varying modulated inputs will rarely land on an exact match — prefer the threshold-style (or-equal) comparisons for triggering instead of exact equality.

## Usage tips

- Use this to build a threshold trigger from two arbitrary modulation sources (e.g. an LFO crossing a fixed level) without a dedicated comparator pattern/effect.
- For beat/level detection against audio specifically, prefer BandGate, which adds hysteresis (a re-arm floor) that this modulator lacks.
