---
class: heronarts.lx.pattern.form.PlanesPattern
kind: pattern
sourceRepo: LX
sourcePath: src/main/java/heronarts/lx/pattern/form/PlanesPattern.java
sourceSha256: db135379cbdfd619838bcabf992f01b7ed73e7017bac3d4c8eaf99e554ba6079
classBytesSha256: 6ae2ae9e2b4c3096f2d3ba660eca9d328db177589587c38ba28b6942c71daec2
classBytesOrigin: ~/.m2/repository/com/heronarts/lx/1.2.1/lx-1.2.1.jar
lxVersion: 1.2.1
generatedAt: 2026-07-09T00:00:00Z
generator: lx-mcp-catalog/1 (claude-sonnet-4-6)
tags: geometric, masking, motion, generative
---

## Summary

PlanesPattern renders up to eight independently configurable luminous planes simultaneously additive-composited onto a black background. Each plane is a thin slab through 3D space defined by its axis orientation (X, Y, Z, free-form Ax+By+Cz+D, or one of two radial modes), position along that axis, and half-width; pixels within the slab receive a brightness contribution that falls off to zero across a contrast-controlled edge. The entire coordinate space shares a global yaw/pitch/roll rotation matrix, so all active planes rotate together, while individual planes also carry per-plane tilt and spin angles that modify their orientation around a pivot point within the slab.

## Parameter interactions

Position and width are the primary per-plane shape parameters — position sweeps the slab through the model and width controls how thick it is; contrast then determines how hard or soft the edge of each slab appears. The tilt and tiltPosition parameters rotate the slab around a secondary axis within its plane, which creates angled slices rather than axis-aligned cuts; spin and spinPosition add a third rotation around a perpendicular axis, enabling arbitrarily oriented planes. Min/max range parameters on both position and width allow LFO-attached modulation to be bounded to a useful region of the model. In free and radial modes the plane's coefficients are specified directly, bypassing the rotation-based controls.

## Usage tips

PlanesPattern excels as a reveal or beam pattern when animated with LFOs attached to position — a single Y-axis plane sweeping slowly upward creates a clean horizontal wash that looks effective on tall structural elements. Multiple planes at different positions and speeds produce a comb or scan effect. The radial modes (R-center and R-origin) create expanding/contracting sphere and origin-centered shells rather than planar slabs. Keep brightness levels on individual planes low when stacking all eight to avoid saturating highlights; the planes add together so total brightness scales with the count of active planes.
