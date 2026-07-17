# OSC addressing in LX (and how chromatik-mcp exposes it)

Distilled from LX source while building PR-5c. The short version: **a parameter's OSC
address is usually its canonical path — except under modulators, where the segments are
label-based.** Tools expose the real address as `oscAddress` so callers never have to
guess.

## How LX derives an OSC address

- `LXOscEngine.getOscAddress(LXParameter)` (LXOscEngine.java:375) returns the parent
  component's `getOscAddress()` + `/` + the parameter's `getPath()` — or **null** when the
  parent isn't an `LXOscComponent` or the parameter isn't a valid OSC parameter. Treat the
  field as optional.
- `LXComponent.getOscAddress()` defaults to `getCanonicalPath()` (LXComponent.java:716), so
  for ordinary components (channels, patterns, palette, …) the OSC address **is** the
  canonical path: `/lx/mixer/channel/1/fader` works for both.
- **`LXModulator` overrides this** (LXModulator.java:225): its OSC segment is
  `getOscPath()`, which falls back to `getOscLabel()` — the modulator's *label* sanitized
  by replacing runs of whitespace and `#*,/\?[]{}` with `-` (LXComponent.java:707).

## The consequence for macro knobs

A global MacroKnobs bank labeled "Knobs" has:

| | address |
|---|---|
| canonical path (MCP tools, `LXPath.get`) | `/lx/modulation/modulator/1/macro1` |
| OSC address (what a controller sends to) | `/lx/modulation/Knobs/macro1` |

Device-scoped modulators nest the same way under the device's path, e.g.
`/lx/mixer/channel/1/pattern/1/modulation/Knobs/macro1`.

Two hazards follow from label-based addressing:

- **Renaming a modulator moves its OSC address.** The canonical path is stable across
  renames; the OSC address is not.
- **Duplicate labels can collide.** Two modulators labeled "Knobs" in the same engine
  produce the same OSC prefix; OSC dispatch resolves by label lookup, so address the one
  you mean by renaming first.

## Ports

`lx.engine.osc` listens on `receivePort` (default **3030**) when `receiveActive` is on, and
transmits on `transmitPort` (default **4040**) when `transmitActive` is on
(LXOscEngine.java:62-63). Both are runtime parameters.

## How chromatik-mcp surfaces this

- `get_parameter` / `set_parameter` payloads carry `oscAddress` (omitted when LX exposes
  none) alongside the canonical `path`.
- `add_modulator`'s response lists every parameter of the new modulator with `path`,
  `label`, `type`, and `oscAddress` — for MacroKnobs that is the OSC channel name of each
  knob, in one call.
- `get_project_info` reports the `osc` block (`receivePort`, `receiveActive`,
  `transmitPort`, `transmitActive`).
