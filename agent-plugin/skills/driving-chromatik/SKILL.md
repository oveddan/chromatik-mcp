---
name: driving-chromatik
description: House rules for driving a live Chromatik (LX Studio) lighting show over the chromatik-mcp MCP server — connecting and recovering from restarts, canonical-path addressing, timeout and error-code semantics, consulting component docs before reasoning about a pattern, and the mutate/look/adjust verification loop. Use whenever calling chromatik MCP tools against a running instance.
---

## When this applies

Use this whenever you're calling chromatik-mcp tools against a running Chromatik (LX
Studio) instance — connecting, discovering the mixer/pattern tree, mutating parameters,
wiring modulation, or checking the render. It's house rules for driving the show well,
not the tool schemas themselves — call `list_channels`, `list_parameters`, and friends to
discover those live.

## Connect and confirm

The server's port lives in `~/.chromatik-mcp/status.json` (pid, port, host, url,
projectPath, lxVersion, serverVersion, buildTime, connected, lastActivityAt) — the one
filesystem touchpoint the plugin writes on startup. Read it before connecting.

To pin a stable port instead of chasing an ephemeral one, put `{"port": 3232}` in
`~/.chromatik-mcp/config.json`. This plugin's bundled `.mcp.json` points at
`http://127.0.0.1:${CHROMATIK_MCP_PORT:-3232}/mcp`, so set `CHROMATIK_MCP_PORT` in your
environment if you use a different port — but that variable only applies under Claude
Code. Under Codex the port is a literal `3232` with no expansion, so pin the port in
`~/.chromatik-mcp/config.json` instead of relying on the environment variable.

Once connected, call `get_status` first: it reports the running server's own identity
(name, jar version, build time, LX version) plus live connection info, and a successful
call also proves the engine loop is draining tasks. Compare its identity against what you
expect before trusting anything else it tells you — a stale process left over from a jar
reinstall looks alive but is running old code.

## Discover, never guess

Every component and parameter is addressed by its canonical LX path (e.g.
`/lx/mixer/channel/1/fader`). Get it from a discovery call — `list_channels`,
`list_parameters`, `get_views`, `list_available_*` — never fabricate one.

Sibling indices are 1-based and shift when items are added, removed, or reordered. A path
held from before a structural mutation (add/remove/move a channel, pattern, effect, view,
fixture) may point at the wrong component afterward — re-list instead of reusing it.

Calls are cheap but not free: each occupies the LX engine thread. Batch discovery — one
`list_channels` beats thirty `get_parameter` calls when you're surveying a mixer tree.

## Assume restarts

Any connection failure — refused connection, a dead session, a tool call erroring where
it didn't before — means "Chromatik may have restarted," not "retry the same call."
Recover in order: re-read `status.json` (the port may have changed), re-initialize the
MCP session against whatever port it now names, and re-list before reusing any canonical
path held from before the restart.

A restart also resets engine state you might assume is sticky — `output.enabled`, for
instance, comes back off. Don't infer the show is in the state you left it; ask.
Unsaved work is lost along with it — mutation tools change only the running engine's
in-memory state, so call `save_project` before a restart is likely, not just at session end.

## Timeouts may leave started work in flight

A tool call that times out cancels its task if it is still queued. If the engine thread
already started it, the task cannot be interrupted safely and may still complete; the
timeout message states which outcome occurred. Re-read state after an already-started
timeout; never blind-retry it, or you risk applying the mutation twice.

Dispatch on the `Result` error CODE, not the message text: expected failures return
`isError: true` with a stable `"code: message"` body, where `code` is `not_found`,
`invalid_argument`, or `internal`. Those three are the contract — build error handling
against them, not against wording that can change. See the plugin's bundled
`references/error-codes.md` for the full wire shape.

## Know the instruments

Before reasoning about what a pattern, effect, or modulator actually does — its color
modes, which parameters interact, what a knob's range really controls — call
`get_component_doc` on its class (by `class` name or a live instance's `path`). The
catalog covers exactly the semantics that otherwise get guessed wrong. Staleness lives at
`catalog.stale`, nested under the `catalog` block, not top-level — and it's three-valued:
`true` (code changed since the doc was written — trust the live parameter tree over the
prose), `false` (fresh), or the string `"unknown"` (staleness couldn't be determined; read
the doc but don't treat it as verified). Read the doc first regardless of which value you
get. A registered-but-undocumented class returns `documented: false`, not an error.

## Verify your own work

Don't report success on the strength of an unchecked mutation. Loop:

1. **Mutate.** Most mutating tools verify-and-echo — `set_parameter`'s response echoes
   what actually happened, but which field carries what depends on whether the parameter
   is modulated: unmodulated, `value` is the base value you set and `baseValue` is absent
   entirely; modulated, `value` is the live *effective* reading and `baseValue` is what
   you actually set — read the response instead of assuming the call did what you asked.
2. **Look.** `get_frame` returns a cheap numeric summary — non-black fraction, lit
   fraction, mean brightness, dominant colors, an NxN mean-color grid — on every call,
   and a token-expensive PNG only when you pass `include_image: true`. Use the summary
   in a tight loop; reach for the PNG at checkpoints.
3. **Adjust.** If the frame doesn't match intent, change the parameter and loop back to
   step 1 — against the actual render, not your mental model of what the change should
   have done.

Only report done after the change has landed (echo/readback) *and* looks right (frame).

## The visibility chain

Pixels only reach fixtures when the whole chain is on: pattern contributing → channel
enabled and fader > 0 → master fader > 0 → engine output enabled (`get_project_info`'s
`output.enabled`, set via `output.enabledPath`).

`get_frame` reads the composited mixer frame, which sits upstream of the last two links.
If a change doesn't show up in `get_frame`, only the first two links — pattern
contributing, channel enabled and fader — can be the cause.

The reverse also matters: `get_frame` looking correct does not mean pixels reach
fixtures. Master fader and `output.enabled` are downstream of the frame and invisible to
it — and `output.enabled` is exactly the parameter that comes back off after a restart
(see "Assume restarts" above), so check it directly rather than trusting the render.

## Wiring has no depth by default

A new `wire_modulator` wiring starts at zero range and is inert — it exists but has no
visible effect until depth is set. Pass the optional `range` argument (e.g. `1.0` for
full depth) to apply it immediately, or `set_parameter` on the wiring's returned
`rangePath` afterwards. A wiring LX rejects (e.g. a circular dependency) clears
Chromatik's entire undo history as a side effect — that's LX's own `perform()` behavior,
not a bug in this server.

## Batch carefully

`apply_operations` runs up to 50 mutation-tool calls in one engine frame — useful when
several changes need no half-built state rendered in between. It does not make the batch
atomic:

- Validation is all-or-nothing. A missing/non-array `operations`, an empty or
  oversized (>50) array, or any per-entry validation failure (a non-object entry, a
  non-string `tool`, an unknown/read-only/nested tool name, a non-object `args`) fails
  the whole call with a top-level `invalid_argument` and applies nothing. Check `isError`
  first.
- Past validation, execution is continue-on-error. Op 3 failing doesn't stop ops 1, 2, or
  4 through 20 from applying. Per-op outcomes live in `results[]` (`{index, ok, result}`
  or `{index, ok: false, code, message}`) — check both `isError` and `results`.
- It does not collapse into one undo step — each operation still produces its own undo
  entry (or entries), so undoing an N-operation batch takes roughly N presses of Cmd-Z
  (e.g. a wiring created with `range` is itself two undo steps).
- One failing operation can silently wipe undo/redo history for every earlier operation
  in the *same* batch (LX's `perform()` behavior), even though those earlier operations
  still report `ok: true`.
- All operations share one engine frame, so an I/O-heavy operation (e.g.
  `reload_fixtures`, which re-reads every `.lxf` from disk) stalls the whole batch onto
  that frame and can trip the 30s executor timeout. A queued batch is cancelled; one
  already started may still complete, per the timeout rule above.

## Composing on the arrange timeline

The composition tools read and author the arrange timeline at
`/lx/timeline/composition` (most also accept a grid clip,
`/lx/mixer/channel/N/clip/M`). Timeline positions travel as cursor objects: reads
emit the full `{millis, beatCount, beatBasis, formatted}`; writes take exactly one of
`{millis}` | `{beatCount[, beatBasis]}` | `{bars[, beats, sixteenths]}` (1-indexed) |
`{at: <origin>[, offsetBeats | offsetMillis]}`. Under `TEMPO` timeBase the beat
fields are authoritative and `millis` derives from the clip's fixed `referenceBpm`,
not the live tempo. Two rules hold across every composition tool: **trust the echo**
(setters silently clamp; every mutation payload carries the state read back from the
engine, never your request), and **addresses are positional** (lane paths
`<clipPath>/lane/<n>` and event indices shift on any insert/remove/move — re-list
rather than reuse them).

### Transport & markers

`get_clip` reads the timeline envelope of the composition (default) or a grid clip:
`timeBase`, `referenceBpm`, every marker as a full cursor object, `running`/`pending`,
`laneCount`. A fresh composition has no timeline — `launch_clip` mode `play` errors
until you record something or grow it:
`set_clip_marker {"marker":"playEnd","cursor":{"bars":33}}`. Scrubbing IS
`set_clip_marker` on `insertMarker`; relative nudges use signed
`moveBeats`/`moveMillis` (bounded at the clip start). Marker setters silently clamp —
trust the returned `cursor` (engine read-back) and the `clamped` flag, never your
request; `playEnd` grows length, `truncate` sets it and rebounds the insert marker.
`launch_clip` modes: `play` (immediate, unquantized, from `from` or the playhead),
`automation` (subject to global launch quantization — expect `pending:true` until the
boundary crosses), `launch` (grid-style from playStart with snapshot recall; rejects
`from`). `stop_clip` halts immediately and also cancels a pending quantized launch.
Transport is not undoable with Cmd-Z; marker moves are.

### Locators

Named timeline markers. `list_locators` returns each locator's
`{path, index, label, cursor}` — locator addressing is **1-indexed everywhere** (tool
args, payload `index`, the `locator:<n>` cursor origin, the canonical path), unlike
0-based lane/event indices. Indices are positional: the list re-sorts by cursor on
every add/move, so re-list rather than reuse. `add_locator` / `move_locator` /
`remove_locator` are undoable LXCommands addressed by `index` or exact-unique
`label`, and echo the summary read back from the engine (add's optional `label`
applies outside the undo stack — redo restores unlabeled). `go_locator` mirrors the
app's locator navigation: running → relaunch automation from the locator (quantized);
stopped → move the insert marker there (bounded to composition length, echoed back)
without starting playback. Not undoable. Rename a locator via `set_parameter` on
`<locatorPath>/label`; place other cursors relative to a locator with
`{"at": "locator:<n>", "offsetBeats": ...}`.

### Lane lifecycle

- `add_clip_lane` is idempotent: `kind:"parameter"` + a normalized parameter path, or
  `kind:"pattern"` + a channel path (e.g. `/lx/mixer/channel/1`). If the lane already
  exists you get it back with `alreadyExisted:true` and nothing lands on the undo
  stack.
- Only lanes reporting `removable:true` in `list_clip_lanes` can be removed.
  Bus/globalModulation/colorPalette lanes are auto-managed everywhere; MIDI/pattern
  lanes are additionally permanent on grid clips.
- `move_clip_lane` may not land where you asked: the composition constrains
  parameter/MIDI/pattern lanes to their channel's section and snaps section lanes
  across whole sections. Trust the returned `lane.index`, never your request
  (`moved:false` means it stayed put). Bus lanes are rejected — reorder the channel
  in the mixer instead.
- `set_clip_lane_visible` is editor-only (a hidden lane still plays back) and not
  undoable.
- Lane paths and indices are positional, and `remove_modulator` cascade-removes
  composition lanes recorded against the removed modulator's parameters — after any
  structural change, re-run `list_clip_lanes` instead of reusing held addresses.

### Reading and inserting automation

Automation events have no canonical path — an event's address is the pair
`{lanePath, index}`, where `index` is its absolute 0-based position in the lane's
event list, and it shifts on every insert or remove. Read events with
`get_clip_lane`: `from`/`to` are an inclusive cursor window, `offset`/`limit` page
within the matched set, and the envelope distinguishes a short lane from a narrow
window (`eventCount` is the lane total, `total` the matched count; `truncated: true`
means advance `offset`). Never carry an index across a mutation — re-read the lane,
or pass `atCursor` on event-editing tools to fail safely if the event moved. When
inserting with `add_automation_point`, trust the echo, not your request: the engine
clamps `normalized` to [0,1] (boolean lanes snap to 0/1), so the returned
`normalized`, `cursor`, and `index` are read back from the created event.

### Editing automation points

- Address a point as `{lanePath, index}` (0-based, from the lane read tools). Indices
  are positional and shift on every insert/remove — pass `atCursor` with the cursor
  you previously read for that point to fail safely if the lane changed underneath
  you.
- One `set_automation_point` call edits any combination of: `cursor` (move in time),
  `normalized` (0-1 normalized space, never the raw parameter value; boolean lanes
  snap to 0/1 — the same field name get_clip_lane emits),
  `curve` (`POWER_EASE | POWER_S_CURVE | SMOOTHSTEP | SINUSOIDAL` — the segment
  arriving AT this point from the previous one), `shape` (-1 to 1), or `resetShape`.
- `cursor`+`normalized` together ride ONE undo step (same gesture as the UI's point drag);
  curve and shape edits are each their own Cmd-Z.
- Moves clamp between the neighboring points and the clip bounds — a point can never
  cross a neighbor. To leapfrog one, remove and re-insert instead. Out-of-range
  `normalized`/`shape` are rejected (not clamped) — an out-of-unit number usually means
  you sent a raw parameter value.
- Always trust the echoed `cursor`/`normalized` in the payload over what you sent; compare
  to detect clamps and snaps.

### Removing events and ranges

- `remove_automation_point {lanePath, index, atCursor?}` removes one event by lane +
  0-based index. Pass `atCursor` (the cursor you read for that index) so the call
  fails safely if the lane changed underneath you. MIDI note lanes refuse
  single-event removal (paired note-on/off would orphan) — use `remove_clip_range` on
  them, which removes pairs together.
- `remove_clip_range {lanePath, from, to}` deletes every event in the inclusive
  cursor range on ONE lane — LX has no clip-wide range command, so loop over
  `list_clip_lanes` for a whole-clip cut. It leaves the gap open and never touches
  markers or clip length; follow with `set_clip_marker {marker: "truncate"}` to also
  shorten the clip (remove-then-truncate composes, each step independently undoable).
- `collapse_clip_range {lanePath, from, to}` flattens the envelope inside the range,
  keeping the range's first and last events as the surviving segment.
- An empty range (or a collapse with fewer than three events in range) is a benign
  success with `removedCount: 0` and puts nothing on the undo stack. Reversed
  `from`/`to` is rejected loudly rather than silently matching nothing. After any
  removal, re-read the lane — every later event index shifted.

### Audio, notes, and record-arm

`add_audio_lane` reads a WAV/AIFF from an absolute path **on the machine running
Chromatik** — if you're driving remotely, your local files are invisible to it. The
new lane lands at the TOP of the lane list (shifting every other lane's positional
path), the composition grows to at least the audio length, and an empty composition
gets its timeline enabled as a side effect. The lane's gain and enabled toggles are
ordinary registered parameters — `set_parameter` on the lane path, not a dedicated
tool.

Text-note lanes (`add_notes_lane`, `add_clip_note`, `set_clip_note`) are annotations
only — section names, cues, TODOs — and never affect playback. Note-event edits are
direct engine edits with no Cmd-Z undo, and a move silently clamps between the
neighboring events and the clip length: always trust the echoed cursor in the
response, never assume your requested position stuck.

`set_composition_arm` is the ONLY write path to the record-arm — the flag has no
canonical path, so `set_parameter` cannot reach it. Arming while the composition is
stopped immediately launches it into recording (from the start when empty, from the
playhead when it has content); disarming does not stop a running composition — use
the transport tools for that. Treat arming as a live-recording action, not a passive
toggle.

See the plugin's bundled `references/addressing.md` for canonical-vs-OSC path details and
`references/error-codes.md` for the full `Result` wire shape.
