---
title: Usage examples
description: Task recipes — understanding a project, building show structure, chaining effects, macro mapping, multi-agent patterns.
---

Task recipes for chromatik-mcp: a goal, the tool-call sequence, and the wrinkles the
tool descriptions warn about. Everything mutating is undoable in Chromatik with Cmd-Z
unless noted. Discovery etiquette (canonical paths, re-listing after structural
changes, batching) is covered once on [Driving Chromatik well](../driving/) — read that
first if you're the agent executing these.

## 1. Understand an inherited project

```
get_project_info                        → LX version, channels, OSC ports
list_channels                           → mixer summary: channels, pattern/effect counts, paths
list_channels {detail: full}            → same tree with full per-pattern/effect detail
list_available_patterns                 → what's installable (documented flags included)
get_component_doc {class: heronarts.lx.pattern.texture.NoisePattern}
                                        → what it renders, how its knobs interact
get_parameter {path: /lx/mixer/channel/1/fader}
                                        → value, range, oscAddress
```

`get_component_doc` serves the generated semantic catalog; `catalog.stale: true` in
the response means the component's code changed since the doc was written — trust the
live parameters over the prose. `documented: false` means no entry exists; fall back
to the class's `description` fields from `list_available_*` and the parameter tree.

## 2. Build a channel with patterns

```
add_channel {class: heronarts.lx.pattern.color.GradientPattern}
                                        → channel path (LX focuses/selects it in the UI)
add_pattern {containerPath: <path>, class: heronarts.lx.pattern.texture.SparklePattern}
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
add_effect {containerPath: <channel path>, class: heronarts.lx.effect.BlurEffect}
add_effect {containerPath: <channel path>, class: heronarts.lx.effect.color.ColorizeEffect}
move_effect {path: <effect path>, index: 0}       → reorder the chain
set_parameter {path: <effect path>/enabled, value: false}   → bypass without removing
remove_effect {path: <effect path>}
```

Containers are channels, the master bus (`/lx/mixer/master`), or an individual
pattern's own FX chain. Locked effects refuse removal with `invalid_argument`.

## 4. Map macro knobs (and read their OSC addresses)

The flow that makes external control work — a side-panel knob bank wired onto anything:

```
add_modulator {class: heronarts.lx.modulator.MacroKnobs}
     → path + every knob's canonical path AND OSC address (label-based!)
wire_modulator {sourcePath: <bank>/macro1, targetPath: /lx/mixer/channel/1/fader}
     → undoable mapping; response carries rangePath/polarityPath
set_parameter {path: <modulation>/range, value: 0.5}        → dial the depth
set_parameter {path: <bank>/macro1, value: 0.75}            → turn the knob
```

- Pass `scope: <pattern path>` to `add_modulator` to put the bank *inside* a device
  chain instead of the global side panel; wiring then stays within that device
  (`scope: /lx/modulation` hosts a device-sourced wiring globally).
- OSC: a modulator knob answers at its **label-based** address
  (`/lx/modulation/Knobs/macro1`), not its canonical path — renaming the modulator
  moves the address. Ports are in `get_project_info`.
- `list_modulations {scope?}` discovers existing banks and wirings when you didn't
  create them; `remove_modulation {path}` unwires. `wire_trigger` + `fire_trigger`
  cover boolean pulse wiring and momentary triggers (`set_parameter` rejects those).

## 5. Verify the render

```
get_frame                               → non-black fraction, mean brightness,
                                          dominant colors, NxN mean-color grid
get_frame {include_image: true}         → plus a PNG the model literally looks at
```

The summary is cheap; the PNG is token-expensive — use it at checkpoints, not in
tight loops (`grid` / `width` tune the cost). For the mutate → look → adjust
verification loop, see [Driving Chromatik well](../driving/).

## 6. Multi-agent patterns

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
  human at the console. Agents should still clean up after themselves (`remove_*`),
  but a human can always unwind an agent's session step by step.

Caveat for parallel sessions: mutations are in-memory until the human saves in
Chromatik; there is deliberately no `save_project` tool.

## 7. Capture looks and stay in time

```
add_snapshot {label: "Verse"}           → captures current mixer/pattern/effect/
                                          modulation state as a new snapshot
list_snapshots                          → recall order, plus transitionEnabled/
                                          transitionTimeSecs and the recall* scope booleans
recall_snapshot {path: <snapshot path>} → restores the captured state
get_tempo                               → bpm, clockSource (options: Int/MIDI/OSC),
                                          launchQuantization, beat/bar position
get_views                               → named model subsets a device's `view` can clip to
add_view {label: "Cubes", selector: "cube"}
remove_view {path: <view path>}
```

Wrinkles:

- Cmd-Z after `recall_snapshot` does not reliably restore plain parameter values to
  their pre-recall state — an LX-side ordering quirk in how it builds the undo entry.
  `recall_snapshot` again (or a different snapshot) to get back to a known state
  instead of relying on undo here.
- With `launchQuantization` set to something other than NONE, `fire_trigger` on a
  quantized trigger reports `pending: true` and defers to the next tempo boundary
  instead of firing immediately.
- `remove_view` does not reset a device's `view` selector to Default when the view it
  pointed at is removed — LX only clamps the selector's stored index into the shrunk
  view list, so it silently reassigns to whichever view now sits at that index.
  Re-check `get_views`' `assignments` after removing a view rather than assuming
  affected devices reset.
