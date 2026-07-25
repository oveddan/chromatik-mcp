# Addressing reference

Distilled from `docs/osc-addressing.md` in the chromatik-mcp repo. Read this when you
need to reason about *why* an address looks the way it does, not just which tool to call.

## Canonical path vs. OSC address

A parameter's OSC address is usually its canonical path — except under modulators, where
the segments are label-based. Every chromatik-mcp tool accepts and returns the
**canonical LX path** (`LXPath.getCanonicalPath()` / `LXPath.get(lx, path)` — the two
round-trip), e.g. `/lx/mixer/channel/1/fader`. That's the address to use for every tool
argument; never guess it, get it from a discovery call.

Ordinary components (channels, patterns, the palette, …) have an OSC address identical to
their canonical path — `LXComponent.getOscAddress()` defaults to `getCanonicalPath()`.
`get_parameter` / `set_parameter` and `add_modulator` responses carry `oscAddress`
(omitted when LX exposes none) alongside the canonical `path`, so you never have to derive
one yourself.

## The modulator exception: label-based OSC segments

`LXModulator` overrides `getOscAddress()`: its OSC segment is the modulator's **label**,
sanitized by replacing runs of whitespace and `#*,/\?[]{}` with `-`. The canonical path is
still index-based and stable; only the OSC address is label-based. Example, a global
MacroKnobs bank labeled "Knobs":

| | address |
|---|---|
| canonical path (every chromatik-mcp tool) | `/lx/modulation/modulator/1/macro1` |
| OSC address (what a physical controller sends to) | `/lx/modulation/Knobs/macro1` |

Device-scoped modulators nest the same way under the device's path, e.g.
`/lx/mixer/channel/1/pattern/1/modulation/Knobs/macro1`.

Two hazards follow directly from label-based addressing — they matter if you're
reconciling an OSC controller mapping with what chromatik-mcp tools report:

- **Renaming a modulator moves its OSC address.** The canonical path stays stable across
  a rename; the OSC address does not. Tools continue to work by canonical path regardless.
- **Duplicate labels collide.** Two modulators labeled "Knobs" in the same engine produce
  the same OSC prefix; OSC dispatch resolves by label lookup, so rename one before
  wiring an external controller to it.

## Index shifting

Child arrays are 1-indexed in canonical paths (LX's OSC convention); a list tool's numeric
`index` field is the 0-based Java index of the same entry — for list output, the path is
the address, `index` is informational only. Sibling indices shift whenever an item is
added, removed, or reordered ahead of them in the same list. A path held before a
structural mutation (`add_channel`, `remove_pattern`, `move_effect`, …) can point at the
wrong component afterward — re-run the list/discovery call rather than reusing a cached
path across a structural change.

`index` is not always informational, though: several tools take a load-bearing 0-based
`index` *argument* — `move_pattern`, `move_effect`, `move_swatch`, `add_pattern`,
`remove_midi_mapping`, `move_fixture`, `duplicate_fixture`. Paths are 1-based; these index
arguments are 0-based. Passing a 1-based value to one of them is an easy mistake if you've
internalized "index is informational" from the list case above.

## OSC ports (context, not a chromatik-mcp concern directly)

`lx.engine.osc` listens on `receivePort` (default 3030) when `receiveActive` is on, and
transmits on `transmitPort` (default 4040) when `transmitActive` is on. `get_project_info`
reports this block (`receivePort`, `receiveActive`, `transmitPort`, `transmitActive`) if
you need to reconcile with an external OSC controller or bridge.
