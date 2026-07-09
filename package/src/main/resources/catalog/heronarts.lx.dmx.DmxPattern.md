---
class: heronarts.lx.dmx.DmxPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/dmx/DmxPattern.java
sourceSha256: 5b67adf3850571b74a62ff443e77c4a46aad42db0075a919a26421c2fba70f2c
classBytesSha256: 77360259245109d3be190e10a629e364bc30a451617a7b54f5442ded03fabd21
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: dmx, utility, color
---

## Summary

DmxPattern maps incoming DMX universe data directly to pixel colors, reading three consecutive DMX channels per point starting from a configurable universe and channel offset, and advancing automatically across universe boundaries when a single universe is exhausted. The byte order of the RGB triplets is configurable, making the pattern compatible with fixtures that use non-standard orderings. The result is that the model mirrors whatever color data the DMX engine receives in real time, turning LX into a live DMX visualizer or a bridge from an external lighting console.

## Parameter interactions

The starting universe and channel define the anchor of a sequential mapping that spans as many universes as needed to cover all model points; shifting the channel offset slides the entire mapping within the universe, while shifting the universe advances it by one full 170-pixel block. The byte-order selection is independent of position and reinterprets the same three bytes differently, so changing it on a running show can produce strongly different output hues without moving any other parameter.

## Usage tips

Reach for DmxPattern when an external DMX console or media server is the primary color source and LX is acting as a protocol bridge or visualizer. It is not useful for generative looks because it produces no output of its own beyond what arrives over DMX. Verify the DMX engine is receiving data before debugging apparent blank output. For installations where only a subset of fixtures is DMX-driven, place this pattern on the relevant channel and use the channel blending stack for non-DMX content elsewhere.
