---
class: apotheneum.examples.StripePattern
kind: pattern
sourceRepo: Apotheneum
sourcePath: src/main/java/apotheneum/examples/StripePattern.java
sourceSha256: a4171cc33bcae58043128844082db76970a12154e696b8738c2ff3b0206ee1d7
classBytesSha256: e7c03a72d5b6f59947aad12949ee54367b1baac371ad4d4a4220f182e0b69786
classBytesOrigin: target/classes
lxVersion: 1.2.1
generatedAt: 2026-07-17T00:00:00Z
generator: lx-mcp-catalog/2 (claude-sonnet-5)
tags: geometric, texture, masking, utility
---

## Summary
Minimal example pattern rendering a single soft-edged grayscale stripe/band through 3D space; not Apotheneum-specific — it iterates the whole loaded model directly, with no exterior/interior or cube/cylinder scoping, so it paints every surface uniformly.
- Each frame it rebuilds a rotation-only transform (roll, then pitch, then yaw, around the model center) from three angle controls, then sets each point's brightness by a triangular falloff centered on a configurable position along the rotated axis.

## Parameter interactions
- Yaw, pitch, and roll are sampled continuously and jointly orient the plane the stripe is measured against; because rotation order is fixed (roll, then pitch, then yaw), combined changes tilt the stripe axis rather than each acting as an independent screen-space direction.
- Center shifts the bright band along the rotated axis; it is bipolar so the band can sit on either side of the model's center.
- Width controls falloff steepness inversely — larger width gives a broader, more gradual band, smaller width a narrow, sharp-edged stripe.

## Usage tips
- Grayscale-only output — use as a movable/rotatable soft mask or wipe, layered under a color source or combined via blend modes, rather than as a standalone colored look.
- No per-face scoping — pair with masking or blend effects for a surface-limited stripe on Apotheneum specifically.
