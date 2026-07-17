# Usage examples

What an MCP-connected agent can do with chromatik-mcp, as concrete tool-call flows. These are
written for agent authors: each example is a goal, the call sequence, and the wrinkles
the tool descriptions warn about. Everything mutating is undoable in Chromatik with
Cmd-Z unless noted.

Ground rules that apply to every flow:

- **Address by canonical path** (`/lx/mixer/channel/1/fader`), always obtained from a
  discovery call — paths of siblings shift when items are removed/inserted (1-based
  indices), so re-list rather than reusing cached paths after structural changes.
- **Discover before you mutate**: `get_project_info` → `list_channels` →
  `list_available_*` is the standard opening. `get_component_doc` tells you what a
  pattern/effect actually does before you add it.

## 1. Understand the project

```
get_project_info                        → LX version, channels, OSC ports
list_channels                           → mixer tree: channels, patterns, effects, paths
list_available_patterns                 → what's installable (documented flags included)
get_component_doc {class: heronarts.lx.pattern.texture.NoisePattern}
                                        → what it renders, how its knobs interact
get_parameter {path: /lx/mixer/channel/1/fader}
                                        → value, range, oscAddress
```

`get_component_doc` serves the generated semantic catalog (see
[catalog-format.md](catalog-format.md)); `catalog.stale: true` in the response means the
component's code changed since the doc was written — trust the live parameters over
the prose. `documented: false` means no entry exists; fall back to the class's
`description` fields from `list_available_*` and the parameter tree.

## 2. Build a show structure

```
add_channel {pattern: heronarts.lx.pattern.color.GradientPattern}
                                        → channel path (LX focuses/selects it in the UI)
add_pattern {channel: <path>, type: heronarts.lx.pattern.texture.SparklePattern}
activate_pattern {path: <pattern path>} → switch to it (PLAYLIST mode)
```

Wrinkles:
- `activate_pattern` is only valid in PLAYLIST composite mode. On a BLEND-mode channel
  it returns `invalid_argument` — there, patterns layer instead of switching; toggle a
  pattern's `enabled` parameter with `set_parameter`.
- With a transition blend configured, the response's `active` is `false` until the
  transition lands — don't re-fire.

## 3. Chain effects

Effects run serially in list order — order matters (blur→colorize ≠ colorize→blur):

```
add_effect {container: <channel path>, type: heronarts.lx.effect.BlurEffect}
add_effect {container: <channel path>, type: heronarts.lx.effect.color.ColorizeEffect}
move_effect {path: <effect path>, index: 0}       → reorder the chain
set_parameter {path: <effect path>/enabled, value: false}   → bypass without removing
remove_effect {path: <effect path>}
```

Containers are channels, the master bus (`/lx/mixer/master`), or an individual
pattern's own FX chain. Locked effects refuse removal with `invalid_argument`.

## 4. Map macro knobs (and read their OSC addresses)

The flow that makes external control work — a side-panel knob bank wired onto anything:

```
add_modulator {type: heronarts.lx.modulator.MacroKnobs}
     → path + every knob's canonical path AND OSC address (label-based!)
wire_modulator {source: <bank>/macro1, target: /lx/mixer/channel/1/fader}
     → undoable mapping; response carries rangePath/polarityPath
set_parameter {path: <modulation>/range, value: 0.5}        → dial the depth
set_parameter {path: <bank>/macro1, value: 0.75}            → turn the knob
```

- Pass `scope: <pattern path>` to `add_modulator` to put the bank *inside* a device
  chain instead of the global side panel; wiring then stays within that device
  (`scope: /lx/modulation` hosts a device-sourced wiring globally).
- OSC: a modulator knob answers at its **label-based** address
  (`/lx/modulation/Knobs/macro1`), not its canonical path — renaming the modulator
  moves the address. Ports are in `get_project_info`. See
  [osc-addressing.md](osc-addressing.md).
- `list_modulations {scope?}` discovers existing banks and wirings when you didn't
  create them; `remove_modulation {path}` unwires. `wire_trigger` + `fire_trigger`
  cover boolean pulse wiring and momentary triggers (`set_parameter` rejects those).

## 5. Multi-agent patterns

The server serializes every tool call onto the LX engine thread, so **concurrent
agents are safe** — calls interleave atomically, and each mutation lands as its own
undo step. Patterns that work well:

- **Split by scope**: one agent owns structure (channels/patterns), another owns
  mapping (modulators/wiring), a third drives parameters live. Canonical paths are the
  shared vocabulary — have the structure agent report the paths it created.
- **Discover-then-act handoff**: a cheap agent walks `list_channels` +
  `get_component_doc` and produces a plan; an executor agent applies it. The catalog
  summaries were written for exactly this — behavior descriptions an orchestrator can
  select by.
- **Undo as a safety net**: every command-backed mutation is one Cmd-Z step for the
  human at the console. Agents should still clean up after themselves
  (`remove_*`), but a human can always unwind an agent's session step by step.

Caveat for parallel sessions: mutations are in-memory until the human saves in
Chromatik; there is no `save_project` tool (deliberate — Phase 2).
