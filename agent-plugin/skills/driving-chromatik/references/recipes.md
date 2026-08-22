# Task recipes

Concrete tool-call flows for chromatik-mcp: each recipe is a goal, the call sequence, and
the wrinkles the tool descriptions warn about. Everything mutating is undoable in
Chromatik with Cmd-Z unless noted.

The discovery etiquette these assume — canonical-path addressing, re-listing after
structural changes, batching, verifying your own work — is in [the skill](../SKILL.md).
Read that first; this file is the worked examples.

## 1. Understand the project

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

`get_component_doc` serves the generated semantic catalog (see
[catalog-format.md](../../../../docs/catalog-format.md)); `catalog.stale: true` in the response means the
component's code changed since the doc was written — trust the live parameters over
the prose. `documented: false` means no entry exists; fall back to the class's
`description` fields from `list_available_*` and the parameter tree.

## 2. Build a show structure

```
add_channel {class: heronarts.lx.pattern.color.GradientPattern}
                                        → channel path (LX focuses/selects it in the UI)
add_pattern {containerPath: <path>, class: heronarts.lx.pattern.texture.SparklePattern}
activate_pattern {path: <pattern path>} → switch to it (PLAYLIST mode)
move_channel {path: <channel path>, index: 0}
                                        → reorder the mixer (0-based, post-removal index;
                                          moves a whole group block, preserves membership)
group_channels {paths: [<channel 1 path>, <channel 3 path>]}
                                        → group bus + reordered member paths + oscChanges
add_effect {containerPath: <group path>, class: heronarts.lx.effect.color.ColorizeEffect}
                                        → one effect over the members' composite
```

Wrinkles:
- `activate_pattern` is only valid in PLAYLIST composite mode. On a BLEND-mode channel
  it returns `invalid_argument` — there, patterns layer instead of switching; toggle a
  pattern's `enabled` parameter with `set_parameter`.
- With a transition blend configured, the response's `active` is `false` until the
  transition lands — don't re-fire.
- The mixer list stays flat after grouping: the group and its members all retain
  top-level `/lx/mixer/channel/N` paths, while each member's `group` field points to
  the bus. Grouping reorders members contiguously and is not undoable because LX has
  no explicit-list command; `ungroup_channel` and `ungroup_channels` are undoable.
  Every grouping mutation returns `oscChanges`; re-list before reusing channel paths.
- `group_channels` focuses and selects the new bus. `ungroup_channel` follows a focused
  member to its new index, but removing the final member leaves the now-empty group bus;
  call `ungroup_channels` on that bus to remove it. Dissolving a group rehomes focus and
  selection using LX's normal channel-removal behavior.

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
  moves the address. Ports are in `get_project_info`. See
  [osc-addressing.md](../../../../docs/osc-addressing.md).
- `list_modulations {scope?}` discovers existing banks and wirings when you didn't
  create them. It returns at most 100 wirings by default, ordered as continuous
  modulations then triggers; while `nextCursor` is present, pass it back as `cursor`.
  Modulators repeat on every page, and changing the graph shifts positional cursors, so
  finish paging before wiring or removing anything. `remove_modulation {path}` unwires.
  `wire_trigger` + `fire_trigger`
  cover boolean pulse wiring and momentary triggers (`set_parameter` rejects those).

## 5. See what you made

```
get_frame                               → non-black fraction, lit fraction, mean
                                          brightness, dominant colors, NxN mean-color grid
get_frame {include_image: true}         → plus a PNG the model literally looks at
get_frame {camera: "current"}           → shot from the live viewpoint, not a flat
                                          front/top/side elevation
get_frame {camera: "stage-looking-up"}  → shot from a saved angle (list_cameras)
```

The summary is cheap; the PNG is token-expensive — use it at checkpoints, not in
tight loops (`grid` / `width` tune the cost). This closes the loop: mutate → look →
adjust, against the actual render instead of a mental model.

```
get_camera                              → the viewpoint now, as both an orbit
                                          (theta/phi/radius about a target) and an eye
set_camera {phi: -80}                   → nudge one axis; the rest stay put
set_camera {eye: {...}, target: {...}}  → place the eye by absolute position
save_camera {name: "stage-looking-up"}  → name it; persists with the project
list_cameras / recall_camera / remove_camera
```

Only the grid depends on the viewpoint — the fractions and dominant colors describe the
whole buffer, so a point the camera cannot see still counts toward them.

### Capture a camera move as a GIF

Start from a saved angle so every take begins identically and successive GIFs are
comparable. On one MCP session, recall that start and begin the move:

```
recall_camera {name: "stage-looking-up"}
animate_camera {to: "balcony", durationMs: 6000}
```

`animate_camera` blocks that session's HTTP worker until arrival, but the LX engine keeps
ticking. Use a second MCP session to poll this concurrently in a loop:

```
get_frame {camera: "current", include_image: true, width: 240}
```

Decode the base64 PNG from each response's image content block and write it into the
zero-padded `frame_%03d.png` sequence. `camera.midMove: true` confirms a frame was caught
between the endpoints. In a live Apotheneum test, a 6000 ms move blocked for 6.01 s while
the second session captured 263 frames (about 44/sec); 262 were marked mid-move.

Assemble the sequence with ffmpeg. This exact command turned those 263 frames into a
1.3 MB, 6 s, 240 px looping GIF:

```
ffmpeg -y -framerate 43.7 -i frame_%03d.png \
  -vf "fps=15,scale=240:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer" \
  -loop 0 camera-move.gif
```

Keep the capture width small: frame count times width drives the final file size. Hold the
pattern static if the GIF is meant to demonstrate the camera path; otherwise it records
both camera motion and pattern motion. And be honest about what the artifact proves:
`get_frame` rasterizes flat discs, not Chromatik's actual bulb rendering. The GIF shows
the move clearly, but undersells how the piece really looks — attach it to a PR as motion
evidence, not a visual-fidelity reference.

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
  human at the console, and agents can walk the same history themselves with the
  `undo` / `redo` tools. Agents should still clean up after themselves
  (`remove_*`), but a human can always unwind an agent's session step by step.

Caveat for `undo` / `redo` specifically: the history is **global to the engine**, not
per-session. With several agents (or a human at the console) mutating concurrently, the
step you undo may not be the step you made — the response names the command that moved,
so read it rather than assuming. Prefer an explicit inverse call (`remove_*` what you
added) over `undo` when another party might be working in the same project.

Caveat for parallel sessions: mutations are in-memory until someone saves — the
human in Chromatik, or an agent calling `save_project` (coordinate: it persists
*everyone's* pending work in one write).

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

## 8. Author an arrange composition

The LX 1.2.2 arrange timeline lives at `/lx/timeline/composition`. Two contracts run
through every call in this flow:

- **Positions are cursor objects** — write exactly one of `{millis}`,
  `{beatCount[, beatBasis]}`, `{bars, beats, sixteenths}` (1-indexed, as read off the
  arrange ruler), or `{at: <origin>, offsetBeats/offsetMillis}`.
- **Every mutation echoes the cursor read back from the engine** as the full object
  `{millis, beatCount, beatBasis, formatted}` — setters clamp silently, so the echo is
  the truth, never your request.

The payloads below assume a fresh TEMPO-timeBase composition at the default 120 BPM,
4 beats per bar — so bar 5 = `beatCount` 16 = 8000 ms.

**Give the fresh composition a timeline, and a lane on a channel fader.** A new
composition has no content; pushing `playEnd` out grows it:

```json
set_clip_marker {"marker": "playEnd", "cursor": {"bars": 33}}
add_clip_lane  {"kind": "parameter", "targetPath": "/lx/mixer/channel/1/fader"}
```

`add_clip_lane` is idempotent (re-adding echoes the existing lane with
`alreadyExisted: true`) and returns the lane read back from the engine:

```json
{
  "clipPath": "/lx/timeline/composition",
  "alreadyExisted": false,
  "laneCount": 7,
  "lane": {
    "path": "/lx/timeline/composition/lane/4",
    "index": 3,
    "type": "parameter",
    "label": "Fader",
    "eventCount": 0,
    "uiVisible": true,
    "removable": true,
    "parameterPath": "/lx/mixer/channel/1/fader"
  }
}
```

**Insert automation points.** `normalized` is [0, 1] normalized space, not the raw
parameter value:

```json
add_automation_point {"lanePath": "/lx/timeline/composition/lane/4",
                      "cursor": {"bars": 1}, "normalized": 0.0}
add_automation_point {"lanePath": "/lx/timeline/composition/lane/4",
                      "cursor": {"bars": 5}, "normalized": 1.0}
```

The second insert echoes (the same envelope `set_automation_point` returns):

```json
{
  "lanePath": "/lx/timeline/composition/lane/4",
  "parameterPath": "/lx/mixer/channel/1/fader",
  "timeBase": "TEMPO",
  "index": 1,
  "cursor": {"millis": 8000.0, "beatCount": 16, "beatBasis": 0.0, "formatted": "5.1.1"},
  "normalized": 1.0,
  "curve": "POWER_EASE",
  "shape": 0.0,
  "eventCount": 2
}
```

**Shape the ramp.** `curve` is the interpolation of the segment arriving *at* this
point (`POWER_EASE | POWER_S_CURVE | SMOOTHSTEP | SINUSOIDAL`); `atCursor` makes the
edit fail safely if index 1 no longer sits at bar 5:

```json
set_automation_point {"lanePath": "/lx/timeline/composition/lane/4", "index": 1,
                      "atCursor": {"bars": 5}, "curve": "SMOOTHSTEP", "shape": 0.5}
```

**Set a loop region.** Marker moves return `{marker, cursor, clamped, clip}` — the
full clip envelope rides along because markers are coupled. The loop on/off flag is an
ordinary registered parameter:

```json
set_clip_marker {"marker": "loopStart", "cursor": {"bars": 5}}
set_clip_marker {"marker": "loopEnd",   "cursor": {"bars": 9}}
set_parameter   {"path": "/lx/timeline/composition/loop", "value": true}
```

**Drop named locators.** The locator list re-sorts by cursor on every add or move, so
the echoed 1-indexed `index` is the position in timeline order:

```json
add_locator {"cursor": {"bars": 5}, "label": "Verse"}
add_locator {"cursor": {"bars": 9}, "label": "Chorus"}
```

```json
{
  "path": "/lx/timeline/composition/locator/2",
  "index": 2,
  "label": "Chorus",
  "locatorCount": 2,
  "cursor": {"millis": 16000.0, "beatCount": 32, "beatBasis": 0.0, "formatted": "9.1.1"}
}
```

`go_locator {"label": "Chorus"}` then jumps the transport there — scrubbing the insert
marker when stopped, relaunching playback (subject to launch quantization) when
running.

**Truncate.** `truncate` sets the clip length directly and rebounds the insert marker
into range; read the result off the echoed envelope's `clip.length` /
`clip.insertMarker`:

```json
set_clip_marker {"marker": "truncate", "cursor": {"bars": 17}}
```

Wrinkles:

- Lane paths (`<clipPath>/lane/<n>`) and event indices are **positional** — they shift
  on every insert, remove, or move (and `remove_modulator` cascade-removes lanes it
  recorded). Re-run `list_clip_lanes` / `get_clip_lane` rather than reuse addresses,
  and pass `atCursor` on event edits.
- Marker, locator, lane-lifecycle, and automation-point edits are undoable with Cmd-Z;
  transport (`launch_clip` / `stop_clip` / `go_locator`), `set_composition_arm`, lane
  visibility, and text-note events are not.
- The same lane/event tools also work on grid clips (`/lx/mixer/channel/N/clip/M`) —
  the composition is just the default `path`.
- `add_audio_lane` loads a WAV/AIFF backing track (composing to music);
  `add_notes_lane` + `add_clip_note` annotate sections without affecting playback.
