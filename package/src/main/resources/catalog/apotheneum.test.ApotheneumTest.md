---
class: apotheneum.test.ApotheneumTest
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/test/ApotheneumTest.java
sourceSha256: 57380f95915310c9a7e9b13f9ccbb69e038d11c976e1044ff2d023fd3caa4eb4
classBytesSha256: 4979a6a3528182f6ab0f2d50a026c878b1c18cf58ae9249205e4da7232794b66
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, diagnostic
---

## Summary
Multi-mode installation-mapping diagnostic, not a performance look — verifies strip/net identity and DMX-pixel ordering.
- Target scopes rendering to all nets, cube only, cylinder only, or one selected net (a named 10-column strand); Side controls whether exterior, interior (exterior copied then blanked), or both receive the result.
- Mode picks one of four renderers: Gradient (default) hue-bands each net's columns and burns a two-digit net-number glyph onto the strip so its identity is legible on the installation; Horizontal Stripe lights one row across every targeted column; Vertical Stripe lights one column per net; DMX Channel walks a synthetic 0-255 RGB ramp down each column (direction alternating by column parity) to verify channel-to-pixel ordering.

## Parameter interactions
- Net only takes effect when Target is Single; it selects which 10-column strand is isolated.
- Color only affects Horizontal Stripe and Vertical Stripe modes — Gradient and DMX Channel generate their own colors and ignore it.
- Stripe X only matters in Vertical Stripe mode; Stripe Y only in Horizontal Stripe mode.
- All controls are sampled per frame, not smoothly animated.

## Usage tips
- Use only for installation bring-up: read net-number glyphs in Gradient mode to confirm strand identity, use DMX Channel mode to validate channel ordering, use stripe modes to isolate a row or column.
- Gradient mode is the default and immediately reads as diagnostic "test card" content — avoid selecting this pattern for performance content.
