# lx-mcp

A drop-in LX/Chromatik package for AI-driven show composition over MCP.

**Status**: core parameter + modulation tool surface working end-to-end (discovery, set, macro-knob mapping, OSC addressing); channels/patterns/effects and MIDI-mapping slices in progress. See [docs/build-plan.md](docs/build-plan.md) for the active roadmap and [docs/tool-conventions.md](docs/tool-conventions.md) for the tool-surface conventions.

## Capabilities

Everything is addressed by canonical LX path (e.g. `/lx/mixer/channel/1/fader`), as returned by the discovery tools. Mutations are undoable in Chromatik with Cmd-Z unless noted.

### Discover

| tool | what it returns |
|---|---|
| `get_project_info` | LX version, project file, channel count, OSC engine state (receive/transmit ports + active) |
| `list_channels` | the mixer: channels, master, and each channel's patterns/effects, with paths |
| `list_available_patterns` / `_effects` / `_modulators` | instantiable classes from the LX registry (modulators carry `global`/`device` flags — where they may be added) |
| `list_modulations` | one modulation engine's live modulators and wirings — global side panel by default, or a device's own chain via `scope` |
| `get_parameter` | one parameter: value, type, range, options, units, and its **OSC address** |

### Read & set parameters

`set_parameter {path, value}` dispatches on the parameter's runtime type (number / integer enum index / boolean / string) and rejects what can't be set sanely: aggregate parameters (set a color's `.../hue`, `.../saturation`, `.../brightness` components instead), computed read-only parameters, out-of-range enum indices (LX would silently wrap), and momentary triggers (see `fire_trigger`). The response echoes the **base** value, so set-then-verify works even while modulation rides on top.

### Map macro knobs (and any modulation)

| tool | what it does |
|---|---|
| `add_modulator {type, scope?}` | add e.g. a `MacroKnobs` bank of eight knobs — to the global side panel, or inside a pattern/effect's own chain via `scope`. Response lists every knob with its path and OSC address |
| `wire_modulator {source, target, scope?}` | undoable continuous mapping, e.g. `macro1 → fader`. Engine inferred from the source (a device knob wires within its device; pass `scope: /lx/modulation` to map it onto something outside). Adjust depth/direction afterwards with `set_parameter` on the returned `rangePath`/`polarityPath` |
| `wire_trigger {source, target, scope?}` | boolean pulse wiring (e.g. a `MacroTriggers` macro → a toggle) |
| `remove_modulation {path}` | unwire either kind by the path the wire call returned |
| `fire_trigger {path}` | pulse a momentary trigger (not undoable — it's an action, and the value auto-resets). Under launch quantization the response says `pending: true`; don't re-fire |

The typical macro-mapping flow:

```
add_modulator {type: heronarts.lx.modulator.MacroKnobs}      → knob bank + 8 OSC addresses
wire_modulator {source: <bank>/macro1, target: <fader path>} → undoable mapping
set_parameter {path: <bank>/macro1, value: 0.75}             → turn the knob
```

### OSC

Parameter payloads carry the address an OSC controller must send to. For most parameters it equals the canonical path, but **modulator knobs answer at label-based addresses** (`/lx/modulation/Knobs/macro1`, not `.../modulator/1/macro1`) — renaming a modulator moves its OSC address. Details and hazards: [docs/osc-addressing.md](docs/osc-addressing.md). Ports are in `get_project_info` (defaults: 3030 receive / 4040 transmit).

## Architecture

The jar embeds an HTTP MCP server (official Java MCP SDK, streamable-HTTP on embedded Tomcat) inside the LX runtime as an `LXPlugin`. Any MCP-speaking client — Claude Code, Claude Desktop, Cursor, Codex, custom orchestrators — connects to it directly and calls tools that mutate LX state in-process. No separate Node server, no `.lxp` file editing, no file watcher. Mutations route through `LXCommand`, so every change gets undo for free, and are serialized onto the LX engine thread via `lx.engine.addTask(...)`. The only filesystem touchpoint is `~/.lx-mcp/status.json`, written on startup so clients can discover the HTTP port. The server binds to `127.0.0.1` only — MCP clients must run on the same machine as Chromatik; there is no authentication layer.

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)
```

## License

TBD.
