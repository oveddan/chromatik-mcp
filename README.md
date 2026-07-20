# chromatik-mcp

A drop-in LX/Chromatik package for AI-driven show composition over MCP.

**Status**: the v1 tool surface is working end-to-end — discovery, parameters, tempo, modulation wiring, channels/patterns/effect chains, mixer performance controls (crossfader, cue/aux), palette (read-write), snapshots, model views, render previews, OSC addressing, and a generated semantic catalog of what each component does. MIDI mapping is the remaining slice. See [docs/build-plan.md](docs/build-plan.md) for the roadmap and [docs/tool-conventions.md](docs/tool-conventions.md) for the tool-surface conventions.

**Docs**: [chromatik-mcp.vercel.app](https://chromatik-mcp.vercel.app) — for AI agents, the full docs are available as plain markdown at [chromatik-mcp.vercel.app/llms-full.txt](https://chromatik-mcp.vercel.app/llms-full.txt) ([llms.txt](https://chromatik-mcp.vercel.app/llms.txt) index).

## Quick start

```sh
# download the latest CI-built jar (or: cd package && mvn install -Pinstall)
curl -L --create-dirs -o ~/Chromatik/Packages/chromatik-mcp.jar \
  https://github.com/oveddan/chromatik-mcp/releases/download/latest/chromatik-mcp.jar
# then: enable Chromatik-MCP in Chromatik Preferences → Plugins, restart, and connect:
claude mcp add --transport http chromatik "http://127.0.0.1:$(jq -r .port ~/.chromatik-mcp/status.json)/mcp"
```

The plugin publishes its endpoint in `~/.chromatik-mcp/status.json` (`{pid, port, host, url, projectPath, lxVersion, serverVersion, buildTime, connected, lastActivityAt}`); the endpoint works with any streamable-HTTP MCP client. Enabling the **Chromatik-MCP** plugin is the only checkbox — the UI status section (left pane, GLOBAL tab) enables itself. By default the server binds an ephemeral port on `127.0.0.1`; a fixed port or non-loopback bind (remote clients) can be configured in `~/.chromatik-mcp/config.json` (`{"port": 7770, "host": "0.0.0.0"}`) — **there is no authentication layer, so a non-loopback bind gives anyone on the network full control of the show**. The repo also ships a project-scope [`.mcp.json`](.mcp.json) for Claude Code (pin the port to `3232` per [docs/install.md](docs/install.md#4-connect-an-mcp-client) and it just works). Full walkthrough and troubleshooting: **[docs/install.md](docs/install.md)**. Concrete agent flows — building show structure, chaining effects, macro mapping, multi-agent patterns: **[docs/usage-examples.md](docs/usage-examples.md)**.

## Capabilities

Everything is addressed by canonical LX path (e.g. `/lx/mixer/channel/1/fader`), as returned by the discovery tools. Mutations are undoable in Chromatik with Cmd-Z unless noted.

### Discover

| tool | what it returns |
|---|---|
| `get_status` | server identity + liveness: `serverVersion`, `buildTime` (detect a stale process after installing a new jar), uptime, connection state |
| `get_project_info` | LX version, project file, channel count, OSC engine state, the **output** object (`/lx/output/enabled` — the "Live" toggle pixels won't reach fixtures without — plus brightness and gamma), and **engine globals** (`speed` playback-rate multiplier, `framesPerSecond`) |
| `get_tempo` | the engine clock: bpm, clock source (internal / MIDI-synced / OSC-driven), beats-per-bar, launch quantization (why `fire_trigger` sometimes answers `pending: true`), tap/nudge paths, live beat position, and a beat-pulse path usable as a `wire_trigger` source |
| `list_channels` | the mixer: channels (with `patternMode` playlist/blend and per-pattern `contributing`), master, and every effect chain — including **pattern-hosted effects**. Each channel carries a `controls` block (crossfade group, blend mode, auto-mute, cue/aux, pattern auto-cycle + transition settings) and the top-level `mixer` object holds the **crossfader** (0 = full A, 1 = full B) and cue/aux preview buses — all with settable paths |
| `list_parameters` | every parameter on a component **plus its child components** (a pattern's effects, the palette's swatches) — walk the tree instead of guessing paths |
| `list_available_patterns` / `_effects` / `_modulators` | instantiable classes from the LX registry (modulators carry `global`/`device` flags — where they may be added) |
| `list_modulations` | one modulation engine's live modulators and wirings — global side panel by default, or a device's own chain via `scope` |
| `get_parameter` | one parameter: value, type, range, options, units, and its **OSC address**. Parameters with live modulations report the effective `value` plus the knob's `baseValue` and `modulated: true` |
| `get_palette` | the global color system: active swatch colors (mode + effective color), saved swatches with `recallPath` (fire to recall, honors the transition time), auto-cycle state. Mutations: see the palette & snapshots section |
| `list_snapshots` | saved snapshots (whole-look captures) plus the snapshot engine's recall-scoping and transition settings, all with settable paths |
| `get_views` | model views (see below) |
| `get_frame` | a PNG render of the current output (`include_image`/`grid`/`width` control token cost) — visual feedback without screen access |
| `get_component_doc` | what a pattern/effect/modulator *does* — generated behavior docs from the [semantic catalog](docs/catalog-format.md), with a bytecode-hash `stale` flag so the answer is honest when code has changed. `list_available_*` entries carry `documented` flags |

### Read & set parameters

`set_parameter {path, value}` dispatches on the parameter's runtime type (number / integer / boolean / string) and rejects what can't be set sanely: aggregate parameters (set a color's `.../hue`, `.../saturation`, `.../brightness` components instead), computed read-only parameters, out-of-range enum indices (LX would silently wrap), and momentary triggers (see `fire_trigger`). Discrete/selector parameters also accept an **option name string** — `{"value": "Cylinder"}` maps a device to a view by label, no index lookup needed. The response echoes the **base** value, so set-then-verify works even while modulation rides on top.

Type arguments everywhere (`add_pattern`, `add_effect`, `add_modulator`, `get_component_doc`) accept either the full class name or the short `name` the `list_available_*` tools return; an ambiguous short name errors listing the candidates.

### Build structure: channels, patterns, effect chains

| tool | what it does |
|---|---|
| `add_channel {pattern?}` / `remove_channel {path}` | mixer channels, optionally seeded with a first pattern (LX moves UI focus to a new channel) |
| `add_pattern {channel, type, index?}` / `remove_pattern` / `move_pattern {path, index}` | manage a channel's pattern list |
| `activate_pattern {path}` | switch the active pattern (PLAYLIST mode; BLEND-mode channels layer patterns via their `enabled` params instead) |
| `add_effect {container, type}` / `remove_effect` / `move_effect {path, index}` | effect chains — run serially in list order — on channels, the master bus, or an individual pattern |

Structural paths are 1-based and reindex on remove/insert — re-list rather than reusing cached paths.

### Map macro knobs (and any modulation)

| tool | what it does |
|---|---|
| `add_modulator {type, scope?}` | add e.g. a `MacroKnobs` bank or a `VariableLFO` — to the global side panel, or inside a pattern/effect's own chain via `scope`. Response lists every parameter with its path and OSC address |
| `remove_modulator {path}` | delete a modulator; wirings it sources are removed with it (one undoable step) |
| `wire_modulator {source, target, scope?, range?}` | undoable continuous mapping, e.g. `macro1 → fader` or `LFO → twist`. Pass `range` (-1..1) to give the wiring depth immediately — **a wiring without range is inert**. Engine inferred from the source; adjust later via the returned `rangePath`/`polarityPath` |
| `wire_trigger {source, target, scope?}` | boolean pulse wiring (e.g. a `MacroTriggers` macro → a toggle) |
| `remove_modulation {path}` | unwire either kind by the path the wire call returned |
| `fire_trigger {path}` | pulse a momentary trigger (not undoable — it's an action, and the value auto-resets). Under launch quantization the response says `pending: true`; don't re-fire |

The typical macro-mapping flow:

```
add_modulator {type: heronarts.lx.modulator.MacroKnobs}      → knob bank + 8 OSC addresses
wire_modulator {source: <bank>/macro1, target: <fader path>} → undoable mapping
set_parameter {path: <bank>/macro1, value: 0.75}             → turn the knob
```

### Palette & snapshots: colors and whole looks

The palette is read-write: beyond setting an individual color's `.../hue` / `.../saturation` / `.../brightness` via `set_parameter`, swatches themselves can be managed. Snapshots capture the *entire* current state — mixer, patterns, effects, modulation — as a recallable look.

| tool | what it does |
|---|---|
| `save_swatch` | capture the active swatch's current colors as a new saved swatch (returns its path) |
| `set_swatch {path}` | apply a saved swatch onto the active colors — same effect as firing its `recallPath` (including the transition fade), but undoable |
| `remove_swatch {path}` / `move_swatch {path, index}` | manage the saved-swatch list |
| `add_color` / `remove_color` | add/remove a color slot on a swatch (active swatch by default); a swatch always keeps at least one color |
| `add_snapshot {label?}` | capture the current mixer/pattern/effect/modulation state as a snapshot |
| `recall_snapshot {path, immediate?}` | restore a snapshot — fades over the engine's transition time unless `immediate`. What a recall touches is governed by the engine's recall-scoping toggles (see `list_snapshots`). Caution (LX behavior): Cmd-Z after a recall does not restore the previous parameter values |
| `update_snapshot {path}` | recapture the current state into an existing snapshot |
| `remove_snapshot {path}` | delete a snapshot |

### Model views: spatial composition

Views are named subsets of the model ("Cube Interior", "Faces Exterior"), defined by a tag selector; every channel, pattern, and effect has a `view` parameter that clips its rendering to one — `Default` inherits from the parent (effect → pattern → channel → whole model). This is how one project paints different geometry with different content — or applies an effect to only part of the model.

| tool | what it does |
|---|---|
| `get_views` | every view definition (selector, enabled/priority, normalization/orientation, **live match counts**), which devices currently use each view, and the model's **tag vocabulary** selectors compose from |
| `add_view {label, selector, normalization?, orientation?}` | create a view; the response's `numGroups`/`numFixtures` immediately show what the selector matched (a `warning` flags zero matches) |
| `remove_view {path}` | delete a view. Caution (LX behavior, undo does not fix it): devices mapped to the removed view silently reassign to whatever view takes over that index — remap them to `Default` first |

Selectors are a small CSS-like language over model tags — space for descendant, `,` union, `&` intersect, `;` separate groups, `*` group-by, `tag[n-m]` index ranges (full grammar in the `get_views` description). Map a device with `set_parameter` on its `view` path using the view's label:

```
add_view {label: "Front+Back", selector: "cubeFrontExterior ; cubeBackExterior", orientation: "group"}
set_parameter {path: /lx/mixer/channel/1/pattern/1/view, value: "Front+Back"}
```

### OSC

Parameter payloads carry the address an OSC controller must send to. For most parameters it equals the canonical path, but **modulator knobs answer at label-based addresses** (`/lx/modulation/Knobs/macro1`, not `.../modulator/1/macro1`) — renaming a modulator moves its OSC address. Details and hazards: [docs/osc-addressing.md](docs/osc-addressing.md). Ports are in `get_project_info` (defaults: 3030 receive / 4040 transmit).

## Architecture

The jar embeds an HTTP MCP server (official Java MCP SDK, streamable-HTTP on embedded Tomcat) inside the LX runtime as an `LXPlugin`. Any MCP-speaking client — Claude Code, Claude Desktop, Cursor, Codex, custom orchestrators — connects to it directly and calls tools that mutate LX state in-process. No separate Node server, no `.lxp` file editing, no file watcher. Mutations route through `LXCommand`, so every change gets undo for free (exceptions — swatch recall, trigger fires, snapshot recall (an LX quirk: the undo entry captures post-recall values) — are called out in their tool descriptions), and are serialized onto the LX engine thread. The filesystem touchpoints are `~/.chromatik-mcp/status.json` (written on startup for endpoint discovery) and the optional `~/.chromatik-mcp/config.json` (fixed port / bind host). Default bind is `127.0.0.1` only; there is no authentication layer, so non-loopback binds are at your own risk (a startup warning says as much).

```
tool handler  ──> domain primitive  ──> LXCommand.perform(...)   (mutation with undo)
(MCP-shaped)     (intent, narrow)   ──> direct lx.engine.* edit  (mutation without undo)
                                    ──> read lx.engine.*         (read-only)
```

## License

[MIT](LICENSE). Note the LX framework this plugin targets is separately licensed (free for non-commercial use — see [lx.studio/license](https://lx.studio/license)); this license covers only the chromatik-mcp code.
