---
class: heronarts.lx.pattern.test.TestPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/test/TestPattern.java
sourceSha256: 334de968a8182c42b479c0422d3b8d3d0e8db460587c5efe256542fe8c2a7c00
classBytesSha256: 23c1895aeaca5b89ffe34719f279ba471b4a6e55eaf03a072ecfc74e012b340b
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: utility, test
---

## Summary

TestPattern is a diagnostic utility that lights one pixel white at a time against a black background to help identify and locate individual LEDs, strips, or tagged sub-models in the physical installation. In iterate mode it advances through all model points sequentially at a configurable rate, cycling back to the start, so an observer can walk the space and watch which fixture corresponds to which point index. In fixed-index mode it pins a single pixel by its point index. In tag mode it either lights every point belonging to a named model tag simultaneously or steps through them individually by index.

## Parameter interactions

The mode parameter is the primary switch that changes how the single lit pixel is selected; the rate, fixedIndex, tag, tagAll, and tagIndex parameters are only relevant for their respective modes. Rate in iterate mode controls how many milliseconds each pixel stays lit before advancing, so faster rates sweep quickly for a global check while slower rates give time to walk to each fixture. The CPU test parameter is an unrelated load generator that performs a configurable number of wasted multiplications per frame to simulate render load for performance profiling; it has no visual effect. The pattern is marked not eligible for auto-cycle so it is never automatically replaced by the engine.

## Usage tips

Use TestPattern at commissioning time to verify fixture mapping, identify address offsets, and confirm that the LX model topology matches the physical installation. Tag mode is particularly useful for verifying that fixtures were grouped correctly — lighting an entire tag simultaneously makes it immediately visible which LEDs share that tag. Remove or replace with a performance pattern before going live, since auto-cycle exclusion means it will stay loaded indefinitely once selected. The CPU test feature is only relevant for benchmarking the render pipeline and should be left at zero in all other contexts.
