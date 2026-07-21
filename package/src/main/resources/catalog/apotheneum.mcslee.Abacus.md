---
class: apotheneum.mcslee.Abacus
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/mcslee/Abacus.java
sourceSha256: 473eab644d02ec6c9a4a0db6fed3f8f5a5333aff54c645c76383f89cfab5effc
classBytesSha256: 62d3247a4283d8ba426370f50675d957078ccd424e767f904b4fad6e17885976
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, utility, data-display, numeric, motion, trigger-free
---

## Summary
- Draws a bank-of-digits display: 7 cube digits on the cube's front face, 11 cylinder digits around the cylinder, each digit built from red bead blocks that slide between on/off positions to encode a value like a physical abacus (up to five "ones" beads below a divider line, up to two "fives" beads above it).
- A white divider bar/ring separates the two bead groups on each digit; a bead's toggle is driven by a damped transition with fixed ~250ms sinusoidal easing, so it slides a short distance into position rather than snapping.
- The rendered front face is copied identically onto all four cube faces (exterior and interior), and the cylinder exterior is copied onto the cylinder interior — every exposed surface of a component always shows the same digit readout.

## Parameter interactions
- Each per-digit value parameter independently sets that digit's target bead pattern; there is no shared color or speed control — slide timing and easing shape are fixed in code, not parameterized.
- Changing a digit's value mid-slide retargets the in-flight bead motion smoothly rather than cutting it.
- Cube and cylinder digit banks are fully independent and can display unrelated values at once.

## Usage tips
- Because the front face is mirrored to every cube face, this pattern cannot show different digits on different cube faces.
- Best used as a driven numeric/status display (clock, counter, score) with an external controller setting digit parameters — nothing changes on its own without parameter updates.
