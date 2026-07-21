---
class: apotheneum.patterns.Hyperspace2D
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/patterns/Hyperspace2D.java
sourceSha256: cb2122b6473bcc076fff974767c79a7cd5833be9cc1826c9e269c31590a07937
classBytesSha256: f5907c09939f74732c086313d02d8dc90ac56f17b43626132908c27b6cbc3a50
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: motion, particles, texture, geometric
---

## Summary
2D variant of the star-field effect in ring/column pixel space, rendering to only one shape at a time (cube or cylinder, chosen by Shape); exterior mirrors onto interior, never the other shape.
- Stars pick one random direction at birth that never changes, traveling straight until age exceeds lifespan, they stray outside bounds, or cross a third distinct face region — a star dies after roughly two face-widths.
- Each star keeps a short position history for a fading trail and can twinkle via a sinusoidal oscillation; positions are floored to integer indices, so stars are hard-edged, and out-of-bounds pixels aren't drawn.

## Parameter interactions
- Source X/Y place where stars are born; Source Z is accepted but never read — no visible effect.
- Spread radius (continuous) jitters spawn distance along each star's direction, turning a point source into a small burst.
- Speed and density are continuous; duration sets the base lifespan (randomized 50-150% per star).
- Trail length also gates frame-clear behavior: above zero it swaps a hard black clear for a fast exponential fade each frame, changing background persistence too, not just tail length.
- Twinkle intensity blends a per-star oscillation into brightness (0 disables it); twinkle speed only matters once intensity is nonzero.

## Usage tips
- Prefer this over the 3D Hyperspace pattern for a single-surface burst/fountain from a point, not a whole-installation warp.
- High speed near a face edge causes rapid turnover since stars die after ~two face-boundary crossings; keep speed and spread moderate for readable trails.
- Debug draws a red cross at the source; leave off during performance.
