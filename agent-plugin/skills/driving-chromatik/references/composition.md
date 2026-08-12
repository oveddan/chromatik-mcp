# Arrange-timeline composition reference

Read this before authoring the arrange timeline. The composition tools read and author
`/lx/timeline/composition` (most also accept a grid clip,
`/lx/mixer/channel/N/clip/M`).

Two rules hold across every composition tool, and the skill body states them too because
violating either is the most common way to corrupt a timeline edit:

- **Trust the echo.** Setters silently clamp. Every mutation payload carries the state
  read back from the engine — never your request.
- **Addresses are positional.** Lane paths `<clipPath>/lane/<n>` and event indices shift
  on any insert/remove/move; re-list rather than reuse them.

## Cursors

Timeline positions travel as cursor objects: reads emit the full
`{millis, beatCount, beatBasis, formatted}`; writes take exactly one of
`{millis}` | `{beatCount[, beatBasis]}` | `{bars[, beats, sixteenths]}` (1-indexed) |
`{at: <origin>[, offsetBeats | offsetMillis]}`. Under `TEMPO` timeBase the beat
fields are authoritative and `millis` derives from the clip's fixed `referenceBpm`,
not the live tempo.

## Transport & markers

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

## Locators

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

## Lane lifecycle

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

## Reading and inserting automation

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

## Editing automation points

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

## Removing events and ranges

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

## Audio, notes, and record-arm

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
