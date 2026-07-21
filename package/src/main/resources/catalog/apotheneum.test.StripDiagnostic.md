---
class: apotheneum.test.StripDiagnostic
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/test/StripDiagnostic.java
sourceSha256: f3ede3f60386712189fe81d5a9e9cd607ce499e9aaafa3f6f5ff7511ca3b96a3
classBytesSha256: 5ecf995e9333836c93cee76dfa6d53c7632458658f7a53a3c5b827a0845686c5
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: utility, diagnostic
---

## Summary
Installation wiring/pixel-index diagnostic, not a performance pattern.
- Each frame blacks the whole model, then lights a contiguous run of white pixels (a start index and a count) in every column of both the cube exterior and cylinder exterior, identically across all columns.
- Exterior colors are then copied onto the corresponding interior, so exterior and interior mirror the same test pattern.

## Parameter interactions
- Start and num are sampled each frame (not smoothly animated) and together define the one lit pixel-index range applied to every column simultaneously.
- Mute only takes effect when its toggle is on; when on, it forces the pixel at the mute index to black within the otherwise-lit run — useful for isolating one known-bad pixel.

## Usage tips
- Never use this in a show — it exists to confirm a pixel range renders consistently across every strip and that interior mirroring copies correctly, catching miswired strips, off-by-one indexing, or dead pixels.
- Point performers and AI clients away from it when selecting performance content; reach for it only when debugging physical strip mapping.
