# Tool reference

Every MCP tool the server exposes, generated from the same JSON-Schema `inputSchema` each
tool advertises over the wire — never hand-transcribed, so it can't drift from what a
client actually sees. A connected client gets the authoritative version from `tools/list`;
[tools.json](tools.json) is the same data as machine-readable JSON.

Everything is addressed by canonical LX path (e.g. `/lx/mixer/channel/1/fader`), as
returned by the discovery tools. Mutations are undoable in Chromatik with Cmd-Z unless
noted.

House rules for actually driving a live instance well — discovery etiquette, restart
handling, verifying your own work — are in
[the driving skill](../agent-plugin/skills/driving-chromatik/SKILL.md).

## Index

**Discover**

| tool | | what it does |
|---|---|---|
| [`get_status`](#get_status) | read | The embedded MCP server's own state: bind host/port/url, when it started, uptime, live connection info (whether a client is currently connected, open… |
| [`get_project_info`](#get_project_info) | read | The open LX project: LX version, project file path (absent if never saved), channel count, OSC engine state (receive/transmit ports and whether… |
| [`get_tempo`](#get_tempo) | read | The engine tempo: bpm (settable via set_parameter on bpm.path), clockSource (INTERNAL = free-running clock driven by bpm, MIDI = synced to an… |
| [`list_channels`](#list_channels) | read | List the mixer's channels with their patterns and effects, plus the master bus. Defaults to 'detail: summary' — a compact per-channel shape (path… |
| [`get_channel`](#get_channel) | read | One channel's (or the master bus's) full detail — exactly the shape list_channels reports per entry at 'detail: full', for just this bus. Use this… |
| [`list_parameters`](#list_parameters) | read | List every parameter on the component at a canonical LX path (channel, pattern, effect, modulator, or engine component like the output engine) … |
| [`list_available_patterns`](#list_available_patterns) | read | List every pattern class registered with LX and available to instantiate, with display name, category, and tags. |
| [`list_available_effects`](#list_available_effects) | read | List every effect class registered with LX and available to instantiate, with display name, category, and tags. |
| [`list_available_modulators`](#list_available_modulators) | read | List every modulator class registered with LX and available to instantiate, with display name, category, and tags. |
| [`list_modulations`](#list_modulations) | read | List one modulation engine's live modulators and wirings. Defaults to 'detail: summary' — the wiring graph only (modulators: path/label/class… |
| [`get_parameter`](#get_parameter) | read | Read one parameter by its canonical LX path (e.g. /lx/mixer/channel/1/fader): value, type, range, options, and units. For a parameter with live… |
| [`get_palette`](#get_palette) | read | The global color palette: the active swatch's colors (with current effective color, mode, and the primary/secondary component paths — set… |
| [`list_snapshots`](#list_snapshots) | read | List saved snapshots — whole-look captures of mixer/pattern/effect/modulation state, in recall order — plus the snapshot engine's settings. The… |
| [`get_frame`](#get_frame) | read | See what the model is rendering by reading the composited output buffer. Pass include_image=true to get an actual PNG image of the current frame … |
| [`get_component_doc`](#get_component_doc) | read | Return the semantic catalog entry for an LX pattern, effect, or modulator class: visual summary, parameter interactions, usage tips, and staleness… |

**Camera: where the model is seen from**

| tool | | what it does |
|---|---|---|
| [`get_camera`](#get_camera) | read | Read the current 3D viewpoint — where the model is being looked at from, in both orbit form (theta/phi/radius about a target) and absolute eye… |
| [`set_camera`](#set_camera) | write | Move the 3D viewpoint — the angle the model is seen from. This is what lets a walk-in installation be judged from inside it: put the eye where a… |
| [`animate_camera`](#animate_camera) | write | Move smoothly from the current 3D viewpoint to a saved angle or an explicit camera over durationMs. Pass 'to' as a name from list_cameras (or… |
| [`save_camera`](#save_camera) | write | Name the current viewpoint so it can be returned to exactly. A named angle is what makes successive renders comparable: re-shooting a pattern from… |
| [`list_cameras`](#list_cameras) | read | The viewpoints saved in this project by save_camera, in the order they were first named, each with the same angle fields get_camera reports. Check… |
| [`recall_camera`](#recall_camera) | write | Move the viewpoint to a saved angle (list_cameras reports the names). Shoot successive renders of the same pattern from one recalled angle so the… |
| [`remove_camera`](#remove_camera) | write | Forget a saved viewpoint. The live camera does not move — this only drops the name from the project's saved list. Unknown name returns not_found. Not… |

**Live preview point style**

| tool | | what it does |
|---|---|---|
| [`get_point_style`](#get_point_style) | read | Read the LED point-rendering settings used by Chromatik's live main 3D preview, including point size, sparkle, LED style, gamma, depth, and… |
| [`set_point_style`](#set_point_style) | write | Set one LED point-rendering setting on Chromatik's live main 3D preview. Numeric, boolean, and discrete values follow set_parameter's rules… |

**Save the project & model**

| tool | | what it does |
|---|---|---|
| [`save_project`](#save_project) | write | Persist the running session to a .lxp project file — the only way structure/mixer/modulation changes made over this API survive a restart; until this… |
| [`save_model`](#save_model) | write | "Save Model As": export the project's structure (fixtures, normalization, label config) to an external .lxm file and re-point the project's model… |

**Read & set parameters**

| tool | | what it does |
|---|---|---|
| [`set_parameter`](#set_parameter) | write | Set a parameter by its canonical LX path (e.g. /lx/mixer/channel/1/fader). The value's type must match the parameter: a number for numeric/bounded, a… |

**Undo and redo**

| tool | | what it does |
|---|---|---|
| [`undo`](#undo) | write | Undo the newest command in Chromatik's shared linear history, exactly like one Cmd-Z. History includes command-backed changes from the UI and other… |
| [`redo`](#redo) | write | Redo the newest command in Chromatik's shared linear history, exactly like one Cmd-Shift-Z. History includes command-backed changes from the UI and… |

**Build structure: channels, patterns, effect chains**

| tool | | what it does |
|---|---|---|
| [`add_channel`](#add_channel) | write | Add a new channel to the mixer. Optionally seed it with a first pattern by passing 'class' (from list_available_patterns). Returns the new channel's… |
| [`remove_channel`](#remove_channel) | write | Remove a channel (or group) from the mixer by its canonical path. Undoable in Chromatik with Cmd-Z. |
| [`move_channel`](#move_channel) | write | Move a channel or group to a 0-based destination index in the mixer's flat channel list. The index is interpreted after removing the moved channel or… |
| [`group_channels`](#group_channels) | write | Create a mixer group from a non-empty list of top-level channel paths. The leftmost selected channel determines where the group bus is inserted… |
| [`ungroup_channel`](#ungroup_channel) | write | Pull one member channel out of its mixer group and place it immediately after the remaining group span. Removing the last member leaves an empty… |
| [`ungroup_channels`](#ungroup_channels) | write | Dissolve a mixer group by its canonical path, leaving all members as top-level channels. Returns the removed group's id and former path plus each… |
| [`add_pattern`](#add_pattern) | write | Add a pattern ('class', from list_available_patterns — either the full class name or the short name it lists) to a channel or PatternRack… |
| [`remove_pattern`](#remove_pattern) | write | Remove a pattern by its canonical path. Remaining sibling patterns reindex (their 1-based paths shift), so cached paths go stale — re-list after… |
| [`move_pattern`](#move_pattern) | write | Move a pattern to a new 0-based index within its channel. Moving shifts the 1-based paths of the moved pattern, any sibling it crosses, and… |
| [`activate_pattern`](#activate_pattern) | write | Activate (go to) a pattern on its channel. Only valid when the channel is in PLAYLIST composite mode — callers on BLEND channels receive… |
| [`add_effect`](#add_effect) | write | Add an effect ('class', from list_available_effects — either the full class name or the short name it lists) to a channel, master bus, or pattern.… |
| [`remove_effect`](#remove_effect) | write | Remove an effect from its container by canonical path. Returns invalid_argument if the effect is locked. Undoable in Chromatik with Cmd-Z. |
| [`move_effect`](#move_effect) | write | Move an effect to a new 0-based index within its container (channel, bus, or pattern). Moving shifts the 1-based paths of the moved effect, any… |

**Map macro knobs (and any modulation)**

| tool | | what it does |
|---|---|---|
| [`add_modulator`](#add_modulator) | write | Add a modulator by class name (from list_available_modulators) — e.g. heronarts.lx.modulator.MacroKnobs for a bank of eight mappable knobs, or the… |
| [`remove_modulator`](#remove_modulator) | write | Remove a modulator added by add_modulator, by the canonical path returned when it was added (or by list_modulations). Any wirings (modulations or… |
| [`move_modulator`](#move_modulator) | write | Move a modulator to a new 0-based index within its global or device-local modulation engine. Index 0 is the first (top) entry. Moving shifts the… |
| [`wire_modulator`](#wire_modulator) | write | Wire a continuous modulation from a source parameter (e.g. a macro knob's macro1) onto a target parameter. To use an oscillator/envelope modulator's… |
| [`wire_trigger`](#wire_trigger) | write | Wire a trigger modulation: when the boolean source fires (e.g. a MacroTriggers macro1), the boolean target is pulsed. Both ends must be boolean… |
| [`remove_modulation`](#remove_modulation) | write | Remove a modulation (continuous or trigger) by the canonical path returned when it was wired (e.g. /lx/modulation/modulation/1). Remaining… |
| [`fire_trigger`](#fire_trigger) | write | Fire a momentary trigger by its canonical path — a TriggerParameter, or a momentary boolean like a MacroTriggers macro (the pulse's rising edge fires… |
| [`list_stages`](#list_stages) | read | Every stage on a MultiStageEnvelope modulator (basis/value/shape point), in basis order: 0-based index, basis, value, per-segment shape (exponent… |
| [`add_stage`](#add_stage) | write | Insert an interior stage on a MultiStageEnvelope modulator. basis (rejected unless strictly between 0 and 1, and unless it differs from every… |
| [`remove_stage`](#remove_stage) | write | Remove an interior stage from a MultiStageEnvelope modulator, addressed by {path, index} (index from list_stages). Only interior stages may be… |
| [`set_stage`](#set_stage) | write | Edit one existing stage on a MultiStageEnvelope modulator, addressed by {path, index} (index from list_stages). Applies any combination of basis (new… |

**Palette & snapshots**

| tool | | what it does |
|---|---|---|
| [`save_swatch`](#save_swatch) | write | Capture the active swatch's current colors as a new saved swatch, appended to the end of get_palette's swatches list. Returns the new swatch's… |
| [`set_swatch`](#set_swatch) | write | Apply a saved swatch's colors onto the active swatch, by its canonical path (as returned by save_swatch or listed in get_palette's swatches). Same… |
| [`remove_swatch`](#remove_swatch) | write | Remove a saved swatch by its canonical path (as returned by save_swatch, or listed in get_palette's swatches). The active swatch's current colors are… |
| [`move_swatch`](#move_swatch) | write | Move a saved swatch to a new 0-based index within get_palette's swatches list. Returns invalid_argument if the index is out of range. Other swatches… |
| [`add_color`](#add_color) | write | Add a color slot to a swatch, appended at the end — targets the active swatch (get_palette's activeSwatch) by default, or a saved swatch if swatch is… |
| [`remove_color`](#remove_color) | write | Remove the last color slot from a swatch — targets the active swatch (get_palette's activeSwatch) by default, or a saved swatch if swatch is given. A… |
| [`add_snapshot`](#add_snapshot) | write | Capture the current mixer/pattern/effect/modulation state as a new snapshot, appended to the end of the list. The optional label overrides LX's… |
| [`recall_snapshot`](#recall_snapshot) | write | Recall a snapshot's captured state, restoring the mixer/pattern/effect/modulation values it holds. By default the recall follows the snapshot… |
| [`update_snapshot`](#update_snapshot) | write | Recapture the current mixer/pattern/effect/modulation state into an existing snapshot, overwriting its previously saved values. Undoable in Chromatik… |
| [`remove_snapshot`](#remove_snapshot) | write | Remove a snapshot by canonical path (as returned by add_snapshot/list_snapshots). Snapshots are addressed by a 1-based structural path (e.g.… |

**Model views: spatial composition**

| tool | | what it does |
|---|---|---|
| [`get_views`](#get_views) | read | Named model subsets ('views', at /lx/structure/views/view/<n>) that a device's 'view' selector can clip its rendering to. A device left on 'Default'… |
| [`add_view`](#add_view) | write | Compose a new named model subset ('view', at /lx/structure/views/view/<n>), matched by a tag selector — see get_views for the selector grammar and… |
| [`remove_view`](#remove_view) | write | Remove a view by its canonical path (as returned by add_view/get_views). Devices selecting a different, surviving view are unaffected. But a device… |

**Model & fixtures**

| tool | | what it does |
|---|---|---|
| [`describe_model`](#describe_model) | read | The model tree: the geometry hierarchy the rig renders onto, from the whole installation down to individual points. Each node reports 'tags' (the… |
| [`get_fixture_format`](#get_fixture_format) | read | Return the .lxf fixture-file JSON schema reference: top-level keys, component types (point/points/strip/arc/class/file-reference), the parameter +… |
| [`list_available_fixtures`](#list_available_fixtures) | read | What add_fixture can instantiate: 'classes' (built-in fixture types — pass the simple name, e.g. 'GridFixture', or the full class name, as… |
| [`list_fixtures`](#list_fixtures) | read | The fixture layer: the physical wiring beneath the model tree describe_model reports — each fixture's geometry transform, output protocol wiring, and… |
| [`get_fixture`](#get_fixture) | read | One fixture's full detail: everything list_fixtures reports for it, plus 'parameters' (every parameter it owns — including type-specific ones like a… |
| [`get_output_map`](#get_output_map) | read | The output wiring beneath the fixture tree: for each fixture, its declared protocol/host/port/universe/channel/byteOrder plus a DERIVED channel… |
| [`add_fixture`](#add_fixture) | write | Instantiate a fixture (see list_available_fixtures for what's addable) — exactly one of 'class' (a built-in fixture type, e.g. GridFixture) or 'type'… |
| [`remove_fixture`](#remove_fixture) | write | Remove a fixture by its canonical path (as returned by list_fixtures/add_fixture). Undoable with Cmd-Z. Every remaining fixture's path is POSITIONAL… |
| [`move_fixture`](#move_fixture) | write | Reposition a fixture within lx.structure.fixtures using a 0-based 'index'. Returns invalid_argument if the index is out of range. Undoable with… |
| [`duplicate_fixture`](#duplicate_fixture) | write | Clone a fixture — geometry, output protocol wiring, and (for a JsonFixture) its .lxf-declared parameter values all copy over — in one call, matching… |
| [`set_fixture_params`](#set_fixture_params) | write | Set several of a fixture's parameters in one call — both its registered parameters (x/y/z/yaw/pitch/roll/scale, enabled, brightness, numPoints… |
| [`set_fixture_tags`](#set_fixture_tags) | write | Set a fixture's model tags — the vocabulary get_views' selectors match against (see get_views). Replaces the fixture's whole tag list. Every token is… |
| [`reload_fixtures`](#reload_fixtures) | write | Pick up .lxf fixture files edited on disk with your own file tools — nothing watches the Fixtures folder, so a .lxf edit is otherwise invisible until… |

**MIDI**

| tool | | what it does |
|---|---|---|
| [`list_midi_devices`](#list_midi_devices) | read | List the MIDI input and output ports LX has discovered. Each input carries three independent routing flags: channelEnabled (notes/CCs forwarded to… |
| [`list_midi_mappings`](#list_midi_mappings) | read | List the parameter mappings driven by incoming MIDI. Each entry gives type ('note' or 'cc'), the 0-based MIDI channel (0-15), number (note pitch or… |
| [`list_midi_surfaces`](#list_midi_surfaces) | read | List the instantiated MIDI control surfaces (e.g. an APC40, a MidiFighterTwister) — a surface is a two-way hardware controller LX drives with a… |
| [`list_midi_templates`](#list_midi_templates) | read | List the MIDI templates instantiated in this project. Templates expose named hardware controls as ordinary parameters at paths such as… |
| [`add_midi_template`](#add_midi_template) | write | Add a registered MIDI template to the project. Pass its full or simple class name, template name, or expected MIDI device name as 'class' — for… |
| [`add_midi_mapping`](#add_midi_mapping) | write | Add a MIDI mapping: incoming note-on or control-change messages on a channel drive a parameter, resolved by its canonical LX path (see… |
| [`remove_midi_mapping`](#remove_midi_mapping) | write | Remove a MIDI mapping by its 0-based index into list_midi_mappings. Returns the removed mapping's summary. Remaining mappings reindex afterwards, so… |
| [`set_midi_input`](#set_midi_input) | write | Set one or more of a MIDI input's routing flags by its 0-based index into list_midi_devices' inputs list: channelEnabled (forward notes/CCs to… |
| [`set_midi_surface_enabled`](#set_midi_surface_enabled) | write | Enable or disable a control surface by its 0-based index into list_midi_surfaces. Returns the updated surface in list_midi_surfaces' shape. Not… |

**Composition: the arrange timeline**

| tool | | what it does |
|---|---|---|
| [`get_composition`](#get_composition) | read | The arrange-timeline composition at /lx/timeline/composition: timeBase (ABSOLUTE or TEMPO — decides which cursor fields are authoritative)… |
| [`get_clip`](#get_clip) | read | One clip's timeline envelope: timeBase (ABSOLUTE or TEMPO — decides which cursor fields are authoritative), referenceBpm (the fixed bpm cursor millis… |
| [`list_clip_lanes`](#list_clip_lanes) | read | Every automation lane on a clip: canonical path (always <clipPath>/lane/<n>, 1-indexed — the address every lane tool takes), 0-based index, type… |
| [`add_clip`](#add_clip) | write | Create a clip in an empty grid slot — the verb that brings a slot into being so every other clip tool can address it. containerPath is the bus that… |
| [`remove_clip`](#remove_clip) | write | Remove a grid clip, emptying its slot — its automation lanes, notes, and snapshot go with it. path is a grid clip (/lx/mixer/channel/N/clip/M); the… |
| [`capture_clip`](#capture_clip) | write | Capture the current live state into a clip's snapshot, overwriting whatever it held — the write side of the snapshot that launch_clip mode 'launch'… |
| [`launch_clip`](#launch_clip) | write | Start clip playback. mode 'play' (the default) is immediate and unquantized, from the 'from' cursor or the current playhead — it requires the clip to… |
| [`stop_clip`](#stop_clip) | write | Stop clip playback immediately, bypassing any launch-quantization delay; also cancels a pending quantized launch. Safe to call on a stopped clip… |
| [`launch_scene`](#launch_scene) | write | Fire a whole row of the clip grid at once — every clip at that index across all channels plus the master bus. This is what makes a chapter land… |
| [`set_clip_marker`](#set_clip_marker) | write | Set or nudge one timeline marker on a clip: insertMarker (the scrub/insert position — this IS how you scrub the arrange timeline), loopStart… |
| [`list_locators`](#list_locators) | read | Every locator (named position marker) on the arrange-timeline composition, in timeline order: canonical path (/lx/timeline/composition/locator/<n>)… |
| [`add_locator`](#add_locator) | write | Adds a locator (named position marker) to the arrange-timeline composition at the given cursor, optionally labeled. Returns the new locator's summary… |
| [`remove_locator`](#remove_locator) | write | Removes a locator from the arrange-timeline composition, addressed by exactly one of 1-indexed index or exact label (which must be unambiguous … |
| [`move_locator`](#move_locator) | write | Moves a locator on the arrange-timeline composition to a new cursor position. Address by exactly one of 1-indexed index or exact label (which must be… |
| [`go_locator`](#go_locator) | write | Transport jump to a locator on the arrange-timeline composition, addressed by exactly one of 1-indexed index or exact label (which must be… |
| [`add_clip_lane`](#add_clip_lane) | write | Add an automation lane to a clip. kind 'parameter': targetPath is a normalized parameter (e.g. /lx/mixer/channel/1/fader) and the lane records that… |
| [`remove_clip_lane`](#remove_clip_lane) | write | Remove an automation lane from its clip. path is the canonical lane address (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes. Only lanes… |
| [`move_clip_lane`](#move_clip_lane) | write | Move an automation lane to a new 0-based index within its clip. path is the canonical lane address (<clipPath>/lane/<n>, 1-indexed) from… |
| [`set_clip_lane_visible`](#set_clip_lane_visible) | write | Show or hide an automation lane in the arrange/clip editor UI. Editor-only: a hidden lane still plays back. path is the canonical lane address… |
| [`add_audio_lane`](#add_audio_lane) | write | Add an audio lane to the arrange composition (/lx/timeline/composition), loading an audio file from an absolute path on the Chromatik machine… |
| [`add_notes_lane`](#add_notes_lane) | write | Add a text-notes lane to the arrange composition (/lx/timeline/composition): a lane of timestamped annotation events (section names, cues, TODOs)… |
| [`set_composition_arm`](#set_composition_arm) | write | Set the arrange timeline's record-arm. The arm flag is a bare engine field with no canonical path (/lx/timeline/arm deliberately does not resolve)… |

**Composition events: automation points & ranges**

| tool | | what it does |
|---|---|---|
| [`get_clip_lane`](#get_clip_lane) | read | One automation lane in full: the lane summary (as in list_clip_lanes) plus a paged read of its events. path is the lane address from list_clip_lanes… |
| [`add_automation_point`](#add_automation_point) | write | Insert an automation point on a parameter clip lane (undoable). lanePath is the lane address from list_clip_lanes (<clipPath>/lane/<n>) and must be a… |
| [`set_automation_point`](#set_automation_point) | write | Edit one existing automation point on a parameter clip lane, addressed by {lanePath, index} (lanePath from list_clip_lanes, type 'parameter'; index… |
| [`remove_automation_point`](#remove_automation_point) | write | Remove one event from a clip lane by {lanePath, index}: an automation point on a parameter lane, or any other lane type's event — except MIDI note… |
| [`remove_clip_range`](#remove_clip_range) | write | Delete every event in the cursor range [from, to] (inclusive at both ends) on one clip lane. Lane-scoped by design — LX has no clip-wide range… |
| [`collapse_clip_range`](#collapse_clip_range) | write | Collapse the automation envelope inside [from, to] on one clip lane: removes the interior events, keeping the first and last events in the range as… |
| [`add_clip_note`](#add_clip_note) | write | Insert a text-note event on a textNote lane (lanePath from list_clip_lanes, form <clipPath>/lane/<n>) at a cursor position, with optional length… |
| [`set_clip_note`](#set_clip_note) | write | Edit the text-note event at {lanePath, index}: set its text (note), move it (cursor — clamped between the neighboring events and the clip length)… |

**Batch**

| tool | | what it does |
|---|---|---|
| [`apply_operations`](#apply_operations) | write | Apply up to 50 mutation-tool calls in one MCP round-trip. Every handler already runs on the LX engine thread, so a batch schedules onto it once and… |


## Discover

### `get_status`

_read-only_

The embedded MCP server's own state: bind host/port/url, when it started, uptime, live connection info (whether a client is currently connected, open SSE stream count, last activity time), and the identity of the running server CODE (name, jar version, build time, LX version) — compare these against a freshly-installed jar to detect a stale process that needs a Chromatik restart. A successful call also proves the LX engine loop is draining tasks, since this handler runs on the engine thread like every other tool.

No parameters.

### `get_project_info`

_read-only_

The open LX project: LX version, project file path (absent if never saved), channel count, OSC engine state (receive/transmit ports and whether active), engine output state, engine-global playback settings, and the model's link to an external .lxm file. output.enabled is the engine's "Live" toggle — when false, nothing reaches physical fixtures regardless of mixer state; set it via set_parameter on output.enabledPath. output.gamma/gammaMode control the output gamma curve. engine.speed is a global playback rate multiplier for animations (1.0 = normal); engine.framesPerSecond caps the render loop rate. model.file is the external .lxm this project's structure is bound to — absent when the model is embedded in the project file, and absent when a static model is in use (model.isStatic true). model.name is LX's display label for the model: the .lxm file name when one is linked, "<Embedded in Project>", or "<ClassName>.class" for a static model loaded from a project's staticModel entry — note a static model set at construction reports model.isStatic true while model.name stays "<Embedded in Project>". A trailing '*' on model.name means LX considers the structure dirty, and appears only while a model file is linked. When model.syncModelFile is true, LX rewrites model.file with the ENTIRE current structure every time the project is serialized — not at the moment of mutation. The write lands on the next serialization: a save in Chromatik, or LX's autosave timer, which writes the project copy to the autosave folder but still exports the model to the real model.file — so a shared .lxm can be rewritten with no explicit save at all. That file may be referenced by other projects on the same rig, which this server cannot detect. Do NOT use model.hasUnsavedChanges to decide whether model.file is at risk. It is LX's internal dirty flag, not a diff against the file: some structure edits set it and others do not (renaming a fixture does not, and reload_fixtures never does even though it can replace the whole model from edited .lxf files), while serialization exports whatever is in memory either way. The reliable rule: if model.syncModelFile is true, assume any structure work — add_fixture, set_fixture_params, set_fixture_tags, move_fixture, remove_fixture, duplicate_fixture, reload_fixtures, or set_parameter on a fixture parameter or on one of lx.structure's own normalization parameters — will reach model.file at the next save or autosave. Check model.syncModelFile BEFORE mutating structure. Turning sync off afterwards is not a safe rescue: the project still records the file reference, but LX ignores it when loading a project whose sync is off, so the project reopens as an embedded-model project carrying your edits — and saving again from there drops the reference for good.

No parameters.

### `get_tempo`

_read-only_

The engine tempo: bpm (settable via set_parameter on bpm.path), clockSource (INTERNAL = free-running clock driven by bpm, MIDI = synced to an external MIDI clock, OSC = driven by incoming OSC tempo messages — bpm is read-only under MIDI/OSC), beatsPerBar (time signature), enabled (tempo trigger modulation on/off), and launchQuantization (governs quantized pattern/clip launches — a fire_trigger call on a quantized trigger under a non-NONE quantization reports pending:true and defers to the next tempo boundary rather than firing immediately). tapPath is a momentary trigger: fire it with fire_trigger repeatedly, in rhythm, to learn bpm from the timing between taps. nudgeUpPath/nudgeDownPath are momentary triggers that temporarily bend the tempo while held. triggerSourcePath is a beat pulse usable as a wire_trigger source. beatCount/barCount/beatCountWithinBar/basis report the current position in the running tempo clock.

No parameters.

### `list_channels`

_read-only_

List the mixer's channels with their patterns and effects, plus the master bus. Defaults to 'detail: summary' — a compact per-channel shape (path, id, label, index, type, enabled, fader, patternMode, activePattern, patternCount, effectCount, containerPatternCount, anyLocalModulation, view) that is the right choice for surveying a project; a real project can carry dozens of channels and hundreds of patterns/effects, and the full shape blows past client response limits. Pass 'detail: full' for today's complete shape (controls block, full patterns array with per-pattern effects, effects array). Every entry carries its canonical LX path for use with other tools. Channels have two pattern modes ('patternMode'): 'playlist' plays one pattern at a time — the one with active=true; 'blend' composites all patterns simultaneously — a pattern shows iff enabled=true AND compositeLevel > 0 ('active' is not meaningful in blend mode). In playlist mode 'enabled' only affects auto-cycle eligibility — it does not hide the active pattern. The per-pattern 'contributing' field (full detail only) applies the correct rule for the channel's mode. A contributing pattern is still invisible if its channel is disabled or its fader is 0, or if engine output is off (see get_project_info). Patterns can host their own effect chains — each pattern entry carries its own effects list (e.g. a Gradient Mask living inside a pattern rather than on the channel; full detail only). Every channel entry — at both detail levels, including summary — carries 'containerPatternCount' and 'anyLocalModulation' as channel-wide rollups: they tell you whether hidden structure exists ANYWHERE on the channel, without walking past what this tool already lists. 'containerPatternCount' is how many of the channel's direct patterns are themselves a container (e.g. a PatternRack) whose own child patterns are not in this payload at any detail level — distinct from the channel-level 'patternCount', which counts a channel's own direct patterns, not how many of them are containers; do not conflate the two. 'anyLocalModulation' is true iff any pattern or effect on the channel owns a non-empty device-local modulation engine. Neither rollup says WHICH pattern or effect — for that, use 'detail: full', which puts a 'nestedPatternCount' and 'hasLocalModulation' marker on every pattern and effect entry (call list_parameters on a pattern's path for its 'children' array of nested pattern paths, and list_modulations with scope=<that path> for its local modulation). In summary detail, only the single active pattern in PLAYLIST mode (the 'activePattern' object) carries these per-pattern markers directly — for every other pattern, and for effects (no summary-mode entry; only 'effectCount'), the channel-level rollups are the only summary-detail signal that more exists. The master bus carries 'anyLocalModulation' too (it can only host effects, no patterns, so it has no 'containerPatternCount'). Every channel, pattern, and effect entry — at both detail levels for channels; pattern/effect entries only exist at 'detail: full', plus the summary-mode 'activePattern' object — carries a 'view' object reporting its model-view assignment when there is something to report (the master bus itself never has a 'view' key; it has no view selector, though its own effects do). An entry with NO 'view' key is on Default and renders to the whole model — the settable selector for any channel, pattern, or effect entry is always that entry's own 'path' + '/view', so no key is needed to address it via set_parameter (the master bus is the one exception: it has no view selector at all, so no such path exists for it). When 'view' IS present: 'selected'/'selectedPath' are omitted when the selector is on 'Default' (inherits from its parent: a channel inherits from its group, a pattern/effect inherits from its host channel or pattern; a master-bus effect on Default inherits the whole model, because the master bus has no view of its own to inherit — but it can set its own view, and then renders to that); 'effective'/'effectivePath' are omitted when this component renders to the whole model. Read the combinations: 'selected' present and 'effective' present = this entry sets its own view (they are always the same view in this case); 'selected' absent but 'effective' present = it is on Default and inherited that view from its parent; 'selected' present but 'effective' absent = LX built no view object for the selected view, so this entry falls back to the whole model. LX builds one only when the model is non-empty AND the view is enabled AND its selector string is non-blank — if any of those fails, everything pointing at that view falls back. That is NOT the same as 'the view matches no fixtures': an enabled view with a non-blank selector in a non-empty model still gets a view object even when it currently matches zero fixtures, so 'effective' stays set to it and get_views reports it with numGroups/numFixtures of 0 — detect 'assigned to a view that lights nothing' by checking those counts via 'effectivePath', never by presence/absence. A pattern's own 'view' can override its channel's — do not assume a channel's view describes what its active pattern renders to; check the pattern's own 'view' (or 'activePattern.view' in summary detail). 'effectivePath' joins to get_views, which reports each view's 'selector', 'numGroups', 'numFixtures', 'normalization', and 'orientation' — a pattern whose 'effective' view differs from its channel's may normalize its coordinates differently (not just light a different set of points), so compare 'normalization'/'orientation' between the two views before reasoning about a pattern's coordinate-space behavior. The top-level 'mixer' object is the crossfader performance surface: 'crossfader' runs 0 (full A) to 1 (full B) — only channels whose 'controls.crossfadeGroup' is 'A' or 'B' (not 'BYPASS') are affected by it, blended via 'crossfaderBlendMode'. 'cueA'/'cueB'/'auxA'/'auxB' toggle the crossfade-group preview buses (full detail only): cue is the primary preview output, aux is a secondary/independent preview output — neither affects the main program output. Per-channel and per-pattern-engine blend-mode option lists are identical across channels, so they are reported once at 'mixer.blendModeOptions' / 'mixer.transitionBlendModeOptions' (full detail only) rather than repeated on every channel. Each channel's 'controls' block (full detail only) carries its crossfade-group assignment, blend mode, auto-mute state, and cue/aux preview toggles; 'controls.patternEngine' (playlist/blend channels only — absent on groups) carries auto-cycle and pattern-transition settings, with 'set_parameter' as the mutation path for any of these fields via the accompanying canonical 'path'.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `detail` | string | no | one of: `summary`, `full` | 'summary' (default) for a compact survey-friendly shape, or 'full' for today's complete per-channel payload (controls, full patterns/effects). |

### `get_channel`

_read-only_

One channel's (or the master bus's) full detail — exactly the shape list_channels reports per entry at 'detail: full', for just this bus. Use this instead of scanning list_channels when you already know the path (e.g. from a prior list_channels/get_project_info call): O(1) against this one channel rather than assembling every channel's patterns/effects/controls to discard the rest. Accepts a channel, group, or the master bus's canonical path (e.g. /lx/mixer/channel/1, /lx/mixer/master).

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the channel, group, or master bus, e.g. /lx/mixer/channel/1 or /lx/mixer/master |

### `list_parameters`

_read-only_

List every parameter on the component at a canonical LX path (channel, pattern, effect, modulator, or engine component like the output engine) — names, types, ranges, current values, and each parameter's own canonical path for get_parameter/set_parameter. Use this instead of guessing parameter names. Parameters with live modulations additionally carry baseValue and modulated=true (value is the effective reading). Also lists the component's child components (a pattern's effects, the palette's swatches, a channel's patterns) with their canonical paths — use it to walk the component tree instead of guessing paths.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical LX path of the component, as returned by the list/get tools |

### `list_available_patterns`

_read-only_

List every pattern class registered with LX and available to instantiate, with display name, category, and tags.

No parameters.

### `list_available_effects`

_read-only_

List every effect class registered with LX and available to instantiate, with display name, category, and tags.

No parameters.

### `list_available_modulators`

_read-only_

List every modulator class registered with LX and available to instantiate, with display name, category, and tags.

No parameters.

### `list_modulations`

_read-only_

List one modulation engine's live modulators and wirings. Defaults to 'detail: summary' — the wiring graph only (modulators: path/label/class; modulations and triggers: path/sourcePath/targetPath) — the right choice for surveying a project; a real project can carry dozens of modulators and hundreds of wirings, and the full shape blows past client response limits. Pass 'detail: full' for today's complete shape (per-modulator OSC address/running state/tempoSync, and per-modulation range/polarity/rangePath to adjust depth via set_parameter). A modulator's tempoSync is true when it is synced to the tempo and false when it is free-running; the key is omitted for classes carrying no sync parameter, so absent means the question does not apply — read it as not-applicable, never as free-running. Wirings are paged in continuous-then-trigger order (100 by default, 250 maximum); pass nextCursor back as cursor until it is omitted. Modulators repeat on every page. Defaults to the global engine; pass scope (a device path) for a pattern/effect's own chain. Knob paths derive from a modulator's path (e.g. <path>/macro1).

| param | type | required | constraints | description |
|---|---|---|---|---|
| `scope` | string | no | — | Optional canonical path of a device (its own engine) or a modulation engine; omit for the global engine |
| `detail` | string | no | one of: `summary`, `full` | 'summary' (default) for the wiring graph only, or 'full' for today's complete payload (OSC addresses, running state, tempoSync where the modulator has a sync parameter, range/polarity/rangePath) |
| `cursor` | string | no | — | Opaque cursor from the preceding page's nextCursor; omit for the first page |
| `limit` | integer | no | 1–250 | Maximum combined number of continuous modulations and triggers to return |

### `get_parameter`

_read-only_

Read one parameter by its canonical LX path (e.g. /lx/mixer/channel/1/fader): value, type, range, options, and units. For a parameter with live modulations, value is the current effective (modulated) reading, baseValue is the knob's set position, and modulated=true; set_parameter changes the base.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical LX path of the parameter, as returned by the list/get tools |

### `get_palette`

_read-only_

The global color palette: the active swatch's colors (with current effective color, mode, and the primary/secondary component paths — set hue/saturation/brightness via e.g. <primaryPath>/hue), the saved swatches with their labels and recall trigger paths, and transition/auto-cycle settings. Recall a saved swatch with fire_trigger on its recallPath; with transitions enabled the change interpolates over transitionTimeSecs (poll get_palette and watch transition.transitionProgress return to 0 to see it land). Recall is NOT undoable. Palette-linked patterns and effects (color mode 'Palette') follow these colors automatically.

No parameters.

### `list_snapshots`

_read-only_

List saved snapshots — whole-look captures of mixer/pattern/effect/modulation state, in recall order — plus the snapshot engine's settings. The settings include the recallMixer/recallPattern/recallEffect/recallModulation/recallMaster/recallOutput booleans, which scope what a recall_snapshot call is allowed to touch, and transitionEnabled/transitionTimeSecs, which govern whether and how long a recall fades between the current state and the snapshot's saved state. Adjust any of them via set_parameter on the returned paths.

No parameters.

### `get_frame`

_read-only_

See what the model is rendering by reading the composited output buffer. Pass include_image=true to get an actual PNG image of the current frame — use this whenever you need to visually inspect the render (e.g. confirming a pattern/effect change looks right, debugging the mapping, or answering 'what does this look like'). The API always returns a cheap numeric summary (non-black fraction, lit fraction, mean brightness, dominant colors, and an NxN mean-color grid) — the PNG is additional when requested. nonBlackFraction counts any pixel with a nonzero channel, so near-black residuals (e.g. a #101010 blur tail) inflate it even though they read as dark. litFraction excludes those residuals: it counts only pixels whose max channel exceeds litThreshold (default 26, ~10% of full scale — a documented heuristic, not perceptual luminance; raise it to make litFraction stricter) and is the field to use when judging negative space or whether an area actually reads as dark. litThreshold=0 makes litFraction equal to nonBlackFraction (max > 0 is the nonBlack condition); litThreshold=255 makes litFraction always 0.0, since no channel can exceed the maximum. Image content is token-expensive, so default to the numeric summary and only request the PNG when actually looking at the picture matters. Renders either a fixed orthographic plane (the 'view' argument's front/top/side, good for a structural read) or an actual camera ('camera': a name from list_cameras, or 'current' for the live viewpoint get_camera reports and Chromatik's preview shows) — a walk-in piece can only be judged from a viewpoint a visitor would occupy, and no orthographic elevation shows that. The two are mutually exclusive; the response echoes whichever it used. Reads main/cue/aux output buses. Only the grid depends on the viewpoint: the fractions and dominant colors describe the whole buffer, so a point the camera cannot see still counts toward them. If animate_camera is moving the current camera, a current-camera render shoots its interpolated position now and reports camera.midMove=true; it does not stall or wait for arrival. The PNG is an independent filled-disc projection, not Chromatik's GLX preview, so it does not reflect get_point_style/set_point_style settings such as sparkle or LED style.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `view` | string | no | one of: `front`, `top`, `side` | Orthographic view plane (default front: x/y as seen from the front; top: x/z; side: z/y) |
| `width` | integer | no | 64–1024 | Image width in pixels (default 256); height follows the model's aspect ratio |
| `camera` | string | no | — | Render from a camera instead of an orthographic plane: the name of a saved angle (list_cameras) or 'current' for the live viewpoint (get_camera). Rejected together with 'view'. |
| `bus` | string | no | one of: `main`, `cue`, `aux` | Which composited buffer to read (default main) |
| `include_image` | boolean | no | — | Include the PNG rendering (default false — image content is token-expensive; request it explicitly when you need to see the frame) |
| `grid` | integer | no | 1–16 | Grid resolution N for the NxN mean-color summary matrix (default 3) |
| `litThreshold` | integer | no | 0–255 | Max-channel cutoff (0-255) a pixel must exceed to count toward litFraction (default 26). Raising it makes litFraction stricter. 0 makes litFraction equal nonBlackFraction; 255 makes litFraction always 0.0. |

### `get_component_doc`

_read-only_

Return the semantic catalog entry for an LX pattern, effect, or modulator class: visual summary, parameter interactions, usage tips, and staleness metadata. Accepts exactly one of 'class' (the full class name or the short name returned by the list_available_* tools — a short name ambiguous across patterns/effects/modulators is rejected, naming the candidates) or 'path' (the canonical path of a live component instance, e.g. /lx/mixer/channel/1/pattern/1 — its class is looked up and documented). Registered but undocumented classes return documented:false (not an error). catalog.candidates always lists every entry considered for the class, winner first, then the rest ranked by accuracy (bytecode match, then recency) — a single-element array is the common case; more than one appears when, e.g., a stock class is documented by both LX and this plugin, so the choice of winner is auditable. The served summary/parameterInteractions/usageTips are merged, not just the ranked winner's: Summary comes from the ranked winner, but any candidate that names parameterInteractions or usageTips in its curated: frontmatter contributes that section even if it lost the ranking. catalog.merged.grafts reports a section whenever the winning declarer isn't the ranked winner, or two or more candidates contested it (base:false and/or tied:true) — empty is the common case.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `class` | string | no | — | Class name, as returned by list_available_* tools — full class name (e.g. heronarts.lx.pattern.color.GradientPattern) or short name (e.g. GradientPattern). Exactly one of 'class' or 'path' is required. |
| `path` | string | no | — | Canonical path of a live component instance (e.g. /lx/mixer/channel/1/pattern/1) whose class is documented. Exactly one of 'class' or 'path' is required. |

## Camera: where the model is seen from

A walk-in installation is judged from inside it, and `get_frame`'s `front`/`top`/`side`
planes are all outside elevations. The camera puts the eye wherever a visitor would stand.

It is an orbit rig, LX's own: `target` is the look-at point, `radius` the distance out to
the eye, `theta` the azimuth in degrees (0 looks from -Z toward +Z, the same viewpoint as
`get_frame`'s `front` plane) and `phi` the elevation (positive looks down, negative looks
up). Pass `eye` instead to place the camera by absolute position. Up is always +Y.

When Chromatik's UI is running this is the 3D preview a person is watching — one camera,
not a private copy — so `recall_camera` puts the human and the agent on the same
viewpoint. Headless, the viewpoint is held by the server.

Framing a good interior angle takes trial and error, and an unnamed one is unrepeatable.
Named angles are saved in the project file, which makes successive renders comparable
(same angle, so the difference is the pattern's) and gives a PR shared vocabulary.

```
set_camera {eye: {x: 0, y: 0, z: 0}, target: {x: 0, y: 400, z: 0}, fovDegrees: 110}
save_camera {name: "stage-looking-up"}
recall_camera {name: "stage-looking-up"}
animate_camera {to: "stage-looking-up", durationMs: 4000}
```

Camera moves are not `LXCommand`-backed, so none of these appear in Chromatik's undo
history.

### `get_camera`

_read-only_

Read the current 3D viewpoint — where the model is being looked at from, in both orbit form (theta/phi/radius about a target) and absolute eye position. Read this before set_camera to nudge the view from where it already is instead of guessing absolute coordinates. The camera orbits a look-at point: 'target' is that point, 'radius' the distance out to the eye, 'theta' the azimuth in degrees (0 looks from -Z toward +Z, the same viewpoint as get_frame's 'front' plane; increasing theta orbits the eye toward +X) and 'phi' the elevation in degrees (positive looks down from above, negative looks up from below). Up is always +Y. Give 'eye' instead to place the camera by absolute position — mutually exclusive with theta/phi/radius, and converted to the same orbit angle, which the response reports back. Values out of LX's range are clamped (phi to ±89°, fovDegrees to 15-150) and theta wraps, so read the response rather than assuming the request landed verbatim; a radius of 0 is rejected instead, since a camera at its own target has no view direction. 'livePreview' says whether this is the camera a person is actually watching: true when Chromatik's UI is up, so set_camera moves what they see; false in a headless runtime, where the viewpoint is held by this server for get_frame alone. When livePreview is true this reads the preview back live, so it also reports a camera the user just moved by hand.

No parameters.

### `set_camera`

_mutating_

Move the 3D viewpoint — the angle the model is seen from. This is what lets a walk-in installation be judged from inside it: put the eye where a visitor stands and aim from there, rather than reading it off an outside elevation. Every field is optional and defaults to the current camera (get_camera), so a single field nudges one axis. The camera orbits a look-at point: 'target' is that point, 'radius' the distance out to the eye, 'theta' the azimuth in degrees (0 looks from -Z toward +Z, the same viewpoint as get_frame's 'front' plane; increasing theta orbits the eye toward +X) and 'phi' the elevation in degrees (positive looks down from above, negative looks up from below). Up is always +Y. Give 'eye' instead to place the camera by absolute position — mutually exclusive with theta/phi/radius, and converted to the same orbit angle, which the response reports back. Values out of LX's range are clamped (phi to ±89°, fovDegrees to 15-150) and theta wraps, so read the response rather than assuming the request landed verbatim; a radius of 0 is rejected instead, since a camera at its own target has no view direction. When Chromatik's UI is running this moves the 3D preview a person is watching (the response's 'livePreview' says so); headless it moves only the viewpoint this server holds. Framing an interior angle by trial and error is slow — save_camera names the result so later renders can be shot from the same place and actually compared. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `theta` | number | no | — | Azimuth in degrees around +Y; 0 looks from -Z toward +Z. Wraps into [0, 360). |
| `phi` | number | no | — | Elevation in degrees from the XZ plane: positive looks down at the target, negative looks up at it. Clamped to LX's ±89 (the look-at degenerates at the poles), so 'straight up' is phi -89. |
| `radius` | number | no | — | Distance from the eye to the target, in model units; must be greater than 0. Also sets the framing in orthographic projection, where the visible width is radius. |
| `target` | object | no | — | The look-at point in model coordinates (LX's camera center). All three components are required. |
| `eye` | object | no | — | Absolute camera position in model coordinates, as an alternative to theta/phi/radius — pass it with 'target' to aim. Rejected alongside theta/phi/radius. All three components are required. |
| `projection` | string | no | one of: `perspective`, `orthographic` | 'perspective' (a real lens — near geometry looms, which is what makes an interior viewpoint read as interior) or 'orthographic' (parallel, no foreshortening; good for a structural read of the model). |
| `fovDegrees` | number | no | — | Vertical field of view in degrees for perspective projection (LX's 'perspective' lens control): 15 is a long lens, 150 an extreme wide angle. Clamped to 15-150. Carried but unused in orthographic projection. |

### `animate_camera`

_mutating_

Move smoothly from the current 3D viewpoint to a saved angle or an explicit camera over durationMs. Pass 'to' as a name from list_cameras (or 'current' for the viewpoint get_camera reports), or omit it and pass camera fields directly; the two forms are mutually exclusive. Explicit fields default to the current camera, so one field animates a single-axis nudge. The camera orbits a look-at point: 'target' is that point, 'radius' the distance out to the eye, 'theta' the azimuth in degrees (0 looks from -Z toward +Z, the same viewpoint as get_frame's 'front' plane; increasing theta orbits the eye toward +X) and 'phi' the elevation in degrees (positive looks down from above, negative looks up from below). Up is always +Y. Give 'eye' instead to place the camera by absolute position — mutually exclusive with theta/phi/radius, and converted to the same orbit angle, which the response reports back. Values out of LX's range are clamped (phi to ±89°, fovDegrees to 15-150) and theta wraps, so read the response rather than assuming the request landed verbatim; a radius of 0 is rejected instead, since a camera at its own target has no view direction. The call returns only when the camera has arrived, while the LX engine keeps rendering throughout the move. A concurrent get_frame {'camera':'current'} shoots the interpolated position immediately and reports midMove:true. Ease matches LX camera animation: sinusoidal (the default), quadratic, or cubic, blended 50% with linear time. durationMs must be 1-29000. The call blocks until the move finishes; completion time scales with /lx/engine/speed (speed is the move's playback-rate multiplier, so a slower speed — even set mid-move — is a proportionally longer wait), but the call is guaranteed to return rather than hang: it is cancelled automatically if the move makes no progress for a brief interval. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `to` | string | no | — | Destination saved-camera name (case-sensitive; see list_cameras), or 'current'. Rejected together with explicit camera fields. |
| `durationMs` | integer | yes | — | Move duration in milliseconds, 1-29000. Actual wait scales with /lx/engine/speed. |
| `ease` | string | no | one of: `sinusoidal`, `quadratic`, `cubic` | LX ease curve (default sinusoidal); each is blended 50% with linear time. |
| `theta` | number | no | — | Azimuth in degrees around +Y; 0 looks from -Z toward +Z. Wraps into [0, 360). |
| `phi` | number | no | — | Elevation in degrees from the XZ plane: positive looks down at the target, negative looks up at it. Clamped to LX's ±89 (the look-at degenerates at the poles), so 'straight up' is phi -89. |
| `radius` | number | no | — | Distance from the eye to the target, in model units; must be greater than 0. Also sets the framing in orthographic projection, where the visible width is radius. |
| `target` | object | no | — | The look-at point in model coordinates (LX's camera center). All three components are required. |
| `eye` | object | no | — | Absolute camera position in model coordinates, as an alternative to theta/phi/radius — pass it with 'target' to aim. Rejected alongside theta/phi/radius. All three components are required. |
| `projection` | string | no | one of: `perspective`, `orthographic` | 'perspective' (a real lens — near geometry looms, which is what makes an interior viewpoint read as interior) or 'orthographic' (parallel, no foreshortening; good for a structural read of the model). |
| `fovDegrees` | number | no | — | Vertical field of view in degrees for perspective projection (LX's 'perspective' lens control): 15 is a long lens, 150 an extreme wide angle. Clamped to 15-150. Carried but unused in orthographic projection. |

### `save_camera`

_mutating_

Name the current viewpoint so it can be returned to exactly. A named angle is what makes successive renders comparable: re-shooting a pattern from 'stage-looking-up' across tuning passes shows what the change did, while two images shot from slightly different angles mostly show the camera move. It is also shared vocabulary — a PR can say which angle a render came from and a reviewer can reproduce it. Saves the live camera by default; pass camera fields to save an angle without moving there first. The camera orbits a look-at point: 'target' is that point, 'radius' the distance out to the eye, 'theta' the azimuth in degrees (0 looks from -Z toward +Z, the same viewpoint as get_frame's 'front' plane; increasing theta orbits the eye toward +X) and 'phi' the elevation in degrees (positive looks down from above, negative looks up from below). Up is always +Y. Give 'eye' instead to place the camera by absolute position — mutually exclusive with theta/phi/radius, and converted to the same orbit angle, which the response reports back. Values out of LX's range are clamped (phi to ±89°, fovDegrees to 15-150) and theta wraps, so read the response rather than assuming the request landed verbatim; a radius of 0 is rejected instead, since a camera at its own target has no view direction. Saving an existing name overwrites it (the response's 'replaced' says so). Saved angles live in the project file, so they survive a restart — but like every other edit here they only reach disk when save_project runs. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `name` | string | yes | — | Name for this angle, e.g. 'stage-looking-up'. Matched exactly (case-sensitive) by recall_camera; surrounding whitespace is trimmed. |
| `theta` | number | no | — | Azimuth in degrees around +Y; 0 looks from -Z toward +Z. Wraps into [0, 360). |
| `phi` | number | no | — | Elevation in degrees from the XZ plane: positive looks down at the target, negative looks up at it. Clamped to LX's ±89 (the look-at degenerates at the poles), so 'straight up' is phi -89. |
| `radius` | number | no | — | Distance from the eye to the target, in model units; must be greater than 0. Also sets the framing in orthographic projection, where the visible width is radius. |
| `target` | object | no | — | The look-at point in model coordinates (LX's camera center). All three components are required. |
| `eye` | object | no | — | Absolute camera position in model coordinates, as an alternative to theta/phi/radius — pass it with 'target' to aim. Rejected alongside theta/phi/radius. All three components are required. |
| `projection` | string | no | one of: `perspective`, `orthographic` | 'perspective' (a real lens — near geometry looms, which is what makes an interior viewpoint read as interior) or 'orthographic' (parallel, no foreshortening; good for a structural read of the model). |
| `fovDegrees` | number | no | — | Vertical field of view in degrees for perspective projection (LX's 'perspective' lens control): 15 is a long lens, 150 an extreme wide angle. Clamped to 15-150. Carried but unused in orthographic projection. |

### `list_cameras`

_read-only_

The viewpoints saved in this project by save_camera, in the order they were first named, each with the same angle fields get_camera reports. Check here before framing an interior angle by hand — someone may already have named the one you want. Empty on a project that has never saved one.

No parameters.

### `recall_camera`

_mutating_

Move the viewpoint to a saved angle (list_cameras reports the names). Shoot successive renders of the same pattern from one recalled angle so the differences between them are the pattern's, not the camera's. When Chromatik's UI is running this also moves the preview a person is watching, putting them and the render on the same viewpoint. Unknown name returns not_found. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `name` | string | yes | — | Name of a saved angle, matched exactly (case-sensitive) — see list_cameras |

### `remove_camera`

_mutating_

Forget a saved viewpoint. The live camera does not move — this only drops the name from the project's saved list. Unknown name returns not_found. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `name` | string | yes | — | Name of a saved angle, matched exactly (case-sensitive) — see list_cameras |

## Live preview point style

These settings belong to Chromatik's live GLX preview and have no canonical
`/lx/...` path. They persist with that preview's project state, but are unavailable in a
headless LX runtime. `get_frame` uses a separate filled-disc raster and does not reproduce
sparkle, LED textures, or the other preview-only controls.

### `get_point_style`

_read-only_

Read the LED point-rendering settings used by Chromatik's live main 3D preview, including point size, sparkle, LED style, gamma, depth, and directional controls. Each entry uses the ordinary parameter wire shape (value, type, range, options, units, and formatting), with name in place of a canonical path because UIPointCloud is not an LXComponent. Unavailable headless. These settings affect only the preview a person watches: get_frame uses an independent filled-disc raster and does not show sparkle, LED style, or any other preview point-style setting.

No parameters.

### `set_point_style`

_mutating_

Set one LED point-rendering setting on Chromatik's live main 3D preview. Numeric, boolean, and discrete values follow set_parameter's rules; discrete/enum settings accept an option name string as well as an integer index (for example ledStyle: 'CIRCLE'). The response is the resulting ordinary parameter wire shape plus its setting name. Unavailable headless and not undoable with Cmd-Z. This changes only the preview a person watches: get_frame uses an independent filled-disc raster and does not show sparkle, LED style, or any other preview point-style setting.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `setting` | string | yes | — | Point-style setting name returned by get_point_style (for example sparkleAmount or ledStyle) |
| `value` | number \| boolean \| string | yes | — | New value; discrete/enum settings also accept an exact option name string |

## Save the project & model

Everything built over this API lives only in the running engine until `save_project`
writes a `.lxp` file — restart Chromatik without saving and it's gone. Neither tool is
`LXCommand`-backed (writing a file is an action, not undoable engine state), so neither
appears in Chromatik's undo history. `save_project` writes through to a linked external
model file whenever `get_project_info`'s `model.syncModelFile` is on — `save_model` to a
new path first if that file is shared with other projects on the rig and shouldn't be
touched.

### `save_project`

_mutating_

Persist the running session to a .lxp project file — the only way structure/mixer/modulation changes made over this API survive a restart; until this is called, they exist only in the running engine. path omitted saves in place over the currently open project (invalid_argument if none is open yet — save-as with a path first); a given path resolves relative paths under LX's Projects media folder, absolute paths are used as-is. overwrite (default false) is required to replace an existing file other than the currently open project — omitting it against an existing target returns invalid_argument naming the resolved path instead of clobbering it. Hazard: when the project's model is linked to an external .lxm with syncModelFile on (see get_project_info's model block), saving the project ALSO rewrites that .lxm — even though nothing about this call mentions the model — and that file may be shared by other projects on the rig; check model.syncModelFile first, or save_model to a new path before calling this if you don't want the shared file touched. The response echoes get_project_info's model block so a client can tell, without a separate call, whether this save also rewrote a linked .lxm. This is a file write, not undoable engine state — it does not appear in undo history. In the Chromatik UI, saving over a dirty external model may raise a confirmation dialog; this call does not suppress it. When batched via apply_operations, check this operation's own result — apply_operations reports top-level ok regardless of individual operation failures (see apply_operations' description).

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Target .lxp path. Omit to save in place over the currently open project. Relative paths resolve under LX's Projects media folder; absolute paths are used as-is. |
| `overwrite` | boolean | no | — | Required (true) to replace an existing file other than the currently open project (default false) |

### `save_model`

_mutating_

"Save Model As": export the project's structure (fixtures, normalization, label config) to an external .lxm file and re-point the project's model link at it (LXStructure.exportModel). path omitted re-exports to the currently linked model file (invalid_argument if the model isn't linked to one yet — see get_project_info's model.file); a given path resolves relative paths under LX's Models media folder and moves the link there, absolute paths are used as-is. overwrite (default false) is required to replace an existing file other than the currently linked one — omitting it against an existing target returns invalid_argument naming the resolved path instead of clobbering it. This is the fix for the shared-.lxm hazard save_project's description warns about: export to a NEW path here before calling save_project, rather than disabling syncModelFile, so the .lxm other projects on the rig load is never touched. The response echoes get_project_info's model block so a client can confirm the link moved. model.external false while model.file is set means the link will not survive a reload (see get_project_info's model.file/model.external description). This is a file write, not undoable engine state — it does not appear in undo history. Requires a dynamic structure — invalid_argument if model.isStatic is true, since a static model has no fixture-based model to export.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Target .lxm path. Omit to re-export to the currently linked model file. Relative paths resolve under LX's Models media folder; absolute paths are used as-is. |
| `overwrite` | boolean | no | — | Required (true) to replace an existing file other than the currently linked model file (default false) |

## Read & set parameters

`set_parameter {path, value}` dispatches on the parameter's runtime type (number /
integer / boolean / string) and rejects what can't be set sanely: aggregate parameters
(set a color's `.../hue`, `.../saturation`, `.../brightness` components instead),
computed read-only parameters, out-of-range enum indices (LX would silently wrap), and
momentary triggers (see `fire_trigger`). Discrete/selector parameters also accept an
**option name string** — `{"value": "Cylinder"}` maps a device to a view by label, no
index lookup needed. The response echoes the **base** value, so set-then-verify works
even while modulation rides on top.

`class` arguments everywhere (`add_pattern`, `add_effect`, `add_modulator`,
`get_component_doc`) accept either the full class name or the short `name` the
`list_available_*` tools return; an ambiguous short name errors listing the candidates.

### `set_parameter`

_mutating_

Set a parameter by its canonical LX path (e.g. /lx/mixer/channel/1/fader). The value's type must match the parameter: a number for numeric/bounded, a boolean for toggles, a string for text. Discrete/selector parameters accept either the in-range integer value or an option name string (e.g. a device's 'view' selector accepts the target view's label) — an unknown or ambiguous option name is rejected with the valid options listed. Aggregate parameters (color, MIDI filter) are set via their component paths (e.g. .../hue, .../saturation, .../brightness); momentary triggers fire via fire_trigger. Undoable in Chromatik with Cmd-Z. On a parameter with live modulations the response's value is the effective (modulated) reading — baseValue echoes the value you set.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical LX path of the parameter, as returned by the list/get tools |
| `value` | number \| boolean \| string | yes | — | New value; its type must match the parameter (number, boolean, or string) |

## Undo and redo

Chromatik and every connected MCP client share one linear command history. Each call
moves exactly one command between the undo and redo stacks; it cannot recover an older
change without also undoing every newer command first.

### `undo`

_mutating_

Undo the newest command in Chromatik's shared linear history, exactly like one Cmd-Z. History includes command-backed changes from the UI and other MCP clients, not only this session. Returns changed:false when there is nothing to undo; otherwise command names what was undone. One call affects one command only — it cannot selectively skip newer work. Re-list affected state after undoing structural or move commands because canonical paths may have shifted. Call this tool directly; it is unavailable inside apply_operations so a batch cannot unexpectedly rewrite shared history. If the LX command throws while undoing, the call returns internal after Chromatik also reports the error; LX clears both history stacks, the error reports post-failure canUndo/canRedo, and engine state may be partially changed — inspect affected state before continuing.

No parameters.

### `redo`

_mutating_

Redo the newest command in Chromatik's shared linear history, exactly like one Cmd-Shift-Z. History includes command-backed changes from the UI and other MCP clients, not only this session. Returns changed:false when there is nothing to redo; otherwise command names what was redone. One call affects one command only. Any new command-backed mutation after an undo clears LX's redo stack. Re-list affected state after redoing structural or move commands because canonical paths may have shifted. Call this tool directly; it is unavailable inside apply_operations so a batch cannot unexpectedly rewrite shared history. If the LX command throws while redoing, the call returns internal after Chromatik also reports the error; LX clears both history stacks, the error reports post-failure canUndo/canRedo, and engine state may be partially changed — inspect affected state before continuing.

No parameters.

## Build structure: channels, patterns, effect chains

Effect chains run serially in list order, on channels, the master bus, or an individual
pattern. Structural paths are 1-based and reindex on remove/insert — re-list rather than
reusing cached paths.

### `add_channel`

_mutating_

Add a new channel to the mixer. Optionally seed it with a first pattern by passing 'class' (from list_available_patterns). Returns the new channel's path, id, label, and 0-based index. Note: LX also moves UI focus/selection to the new channel. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `class` | string | no | — | Optional pattern class name (from list_available_patterns) to seed the channel with |

### `remove_channel`

_mutating_

Remove a channel (or group) from the mixer by its canonical path. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the channel to remove, e.g. /lx/mixer/channel/1 |

### `move_channel`

_mutating_

Move a channel or group to a 0-based destination index in the mixer's flat channel list. The index is interpreted after removing the moved channel or entire group block: moving index 0 to index 2 in [A, B, C] produces [B, C, A]. Groups move together with all their members. This tool preserves membership: a grouped channel must stay within its group, and a top-level channel cannot be inserted into a group. Moving shifts 1-based paths for the moved block, crossed siblings, their effects/patterns, bus-level modulation, grid clips and lanes, and corresponding arrange-timeline lanes — re-list rather than reusing cached paths. The response's oscChanges array reports every changed canonical path (componentId, before, after). Returns invalid_argument for an out-of-range index or a destination that would change group membership. Undoable in Chromatik with Cmd-Z; an undo inverts every path in oscChanges with no separate signal.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the channel or group to move, e.g. /lx/mixer/channel/1 |
| `index` | integer | yes | -2147483648–2147483647 | 0-based destination in the mixer list after removing the moved channel/group block |

### `group_channels`

_mutating_

Create a mixer group from a non-empty list of top-level channel paths. The leftmost selected channel determines where the group bus is inserted; members are reordered contiguously in their current mixer order. Rejects duplicate paths, group paths, and channels already in a group. Grouping shifts positional channel paths, including descendants; the response reports every changed canonical path in oscChanges, and callers should re-list channels before reusing cached paths. LX moves main and aux focus to the new group and selects only that bus. LX has no explicit-list grouping command, so this is a direct engine edit. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `paths` | array<string> | yes | — | Non-empty list of top-level channel paths to group; input order does not matter |

### `ungroup_channel`

_mutating_

Pull one member channel out of its mixer group and place it immediately after the remaining group span. Removing the last member leaves an empty group bus; call ungroup_channels on that bus to dissolve it. If the member has main or aux focus, LX follows it to its new index. Returns invalid_argument if the channel is not grouped. The operation shifts positional channel paths, including descendants; the response reports every changed canonical path in oscChanges, and callers should re-list channels before reusing cached paths. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of a channel currently in a group |

### `ungroup_channels`

_mutating_

Dissolve a mixer group by its canonical path, leaving all members as top-level channels. Returns the removed group's id and former path plus each freed channel's current path. Removing the bus makes LX rehome focus and selection using its normal channel-removal rules (typically to the first freed channel or adjacent bus). Dissolving shifts positional channel paths, including descendants; the response reports every changed canonical path in oscChanges, and callers should re-list channels before reusing cached paths. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the group to dissolve, e.g. /lx/mixer/channel/1 |

### `add_pattern`

_mutating_

Add a pattern ('class', from list_available_patterns — either the full class name or the short name it lists) to a channel or PatternRack ('containerPath'). Pass an optional 0-based index to insert at a specific position; omit to append. The first pattern added to an empty container auto-activates. In list_channels detail:'full', a PatternRack entry has nestedPatternCount and its path is the containerPath. Inserting shifts the 1-based paths of later sibling patterns — re-list rather than reusing cached paths. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `containerPath` | string | yes | — | Canonical path of the channel or PatternRack, e.g. /lx/mixer/channel/1 or /lx/mixer/channel/1/pattern/1 when that pattern is a PatternRack |
| `class` | string | yes | — | Pattern class name, as returned by list_available_patterns — full class name or short name |
| `index` | integer | no | -2147483648–2147483647 | 0-based insertion index; omit to append at the end |

### `remove_pattern`

_mutating_

Remove a pattern by its canonical path. Remaining sibling patterns reindex (their 1-based paths shift), so cached paths go stale — re-list after removal. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the pattern, e.g. /lx/mixer/channel/1/pattern/1 |

### `move_pattern`

_mutating_

Move a pattern to a new 0-based index within its channel. Moving shifts the 1-based paths of the moved pattern, any sibling it crosses, and everything those siblings own (their effects, any nested rack patterns and effects, and any device-local modulators/modulations/triggers) — re-list rather than reusing cached paths; the response's oscChanges array reports exactly which canonical paths changed (componentId, before, after). It reports changes only, not components removed during the move. Returns invalid_argument if the index is out of range. Undoable in Chromatik with Cmd-Z, which a human can trigger outside this session's control; an undo inverts every path in oscChanges with no separate signal, so re-list after any move if undo is possible.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the pattern to move, e.g. /lx/mixer/channel/1/pattern/1 |
| `index` | integer | yes | -2147483648–2147483647 | 0-based destination index within the channel's pattern list |

### `activate_pattern`

_mutating_

Activate (go to) a pattern on its channel. Only valid when the channel is in PLAYLIST composite mode — callers on BLEND channels receive invalid_argument (toggle the pattern's enabled parameter instead). With a transition blend configured the switch starts as a transition (active: false until it lands). Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the pattern to activate, e.g. /lx/mixer/channel/1/pattern/2 |

### `add_effect`

_mutating_

Add an effect ('class', from list_available_effects — either the full class name or the short name it lists) to a channel, master bus, or pattern. 'containerPath' must be a channel path (e.g. /lx/mixer/channel/1), the master bus path, or a pattern path (e.g. /lx/mixer/channel/1/pattern/1). Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `containerPath` | string | yes | — | Canonical path of the channel, master bus, or pattern to add the effect to |
| `class` | string | yes | — | Effect class name, as returned by list_available_effects — full class name or short name |

### `remove_effect`

_mutating_

Remove an effect from its container by canonical path. Returns invalid_argument if the effect is locked. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the effect to remove, e.g. /lx/mixer/channel/1/effect/1 |

### `move_effect`

_mutating_

Move an effect to a new 0-based index within its container (channel, bus, or pattern). Moving shifts the 1-based paths of the moved effect, any sibling it crosses, and any device-local modulators/modulations/triggers those siblings own — re-list rather than reusing cached paths; the response's oscChanges array reports exactly which canonical paths changed (componentId, before, after). It reports changes only, not components removed during the move. Returns invalid_argument if the index is out of range. Undoable in Chromatik with Cmd-Z, which a human can trigger outside this session's control; an undo inverts every path in oscChanges with no separate signal, so re-list after any move if undo is possible.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the effect to move, e.g. /lx/mixer/channel/1/effect/1 |
| `index` | integer | yes | -2147483648–2147483647 | 0-based destination index within the effect list |

## Map macro knobs (and any modulation)

### `add_modulator`

_mutating_

Add a modulator by class name (from list_available_modulators) — e.g. heronarts.lx.modulator.MacroKnobs for a bank of eight mappable knobs, or the short name it lists (e.g. VariableLFO for heronarts.lx.modulator.VariableLFO). Pass the class name as 'class'. By default it lands in the global modulation engine (the Chromatik side panel); pass 'scope' to add it inside a pattern/effect's own chain. The response lists every parameter with its canonical path and OSC address. On projects with a large modulation graph, Chromatik's synchronous UI refresh may make this operation slow. If it times out after already starting, it may still complete; inspect state before retrying. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `class` | string | yes | — | Modulator class name, as returned by list_available_modulators — full class name (e.g. heronarts.lx.modulator.VariableLFO) or short name (e.g. VariableLFO) |
| `scope` | string | no | — | Optional canonical path of a pattern/effect to host the modulator in its own chain; omit for the global engine |

### `remove_modulator`

_mutating_

Remove a modulator added by add_modulator, by the canonical path returned when it was added (or by list_modulations). Any wirings (modulations or triggers) sourced from the modulator are removed with it. Remaining modulators in the same engine reindex afterwards, so held paths can go stale. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the modulator, as returned by add_modulator |

### `move_modulator`

_mutating_

Move a modulator to a new 0-based index within its global or device-local modulation engine. Index 0 is the first (top) entry. Moving shifts the 1-based canonical paths of the moved modulator and any sibling it crosses; re-list modulations rather than reusing cached paths. The response's oscChanges array reports exactly which component canonical paths changed (componentId, before, after). Label-based OSC addresses do not change. Returns invalid_argument if the index is out of range. Undoable in Chromatik with Cmd-Z, which a human can trigger outside this session's control; re-list after any move if undo is possible.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the modulator to move, e.g. /lx/modulation/modulator/1 |
| `index` | integer | yes | -2147483648–2147483647 | 0-based destination index within the modulation engine |

### `wire_modulator`

_mutating_

Wire a continuous modulation from a source parameter (e.g. a macro knob's macro1) onto a target parameter. To use an oscillator/envelope modulator's own running value as the source, pass the modulator's own canonical path (it is itself a parameter) — not one of its input sub-parameters like an LFO's basisIn, which only takes effect when that modulator's manualBasis is enabled and otherwise silently does nothing. The target must be a compound parameter (most device/mixer knobs are). Scope is inferred from the source — a knob inside a device chain wires within that device; pass scope explicitly to override. The wiring starts with zero depth and has no visible effect until depth is set — pass the optional range argument (e.g. 1.0 for full depth) to apply it immediately, or set_parameter on the returned rangePath afterwards. Adjust direction via polarityPath. Undoable in Chromatik with Cmd-Z, though a wiring created with range takes two undo steps (depth first, then the wiring). Caution: a wiring LX rejects (circular dependency) clears Chromatik's undo history. Args: 'sourcePath', 'targetPath', optional 'scope' and 'range'.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `sourcePath` | string | yes | — | Canonical path of the source parameter (e.g. a MacroKnobs macro1) |
| `targetPath` | string | yes | — | Canonical path of the target compound parameter |
| `scope` | string | no | — | Optional path of the engine hosting the wiring: a device path (its own engine) or /lx/modulation (global — required to wire a device knob to a target outside its device). Omitted, it is inferred from the source |
| `range` | number | no | — | Optional initial modulation depth, -1.0 to 1.0; without it the wiring starts at 0 and is inert |

### `wire_trigger`

_mutating_

Wire a trigger modulation: when the boolean source fires (e.g. a MacroTriggers macro1), the boolean target is pulsed. Both ends must be boolean parameters. Scope is inferred from the source like wire_modulator. Undoable in Chromatik with Cmd-Z. Caution: a wiring LX rejects (circular dependency) clears Chromatik's undo history. Args: 'sourcePath', 'targetPath', optional 'scope'.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `sourcePath` | string | yes | — | Canonical path of the boolean source parameter |
| `targetPath` | string | yes | — | Canonical path of the boolean target parameter |
| `scope` | string | no | — | Optional path of the engine hosting the wiring: a device path (its own engine) or /lx/modulation (global — required to wire a device trigger to a target outside its device). Omitted, it is inferred from the source |

### `remove_modulation`

_mutating_

Remove a modulation (continuous or trigger) by the canonical path returned when it was wired (e.g. /lx/modulation/modulation/1). Remaining modulations in the same engine reindex afterwards, so held paths can go stale. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the modulation, as returned by wire_modulator/wire_trigger |

### `fire_trigger`

_mutating_

Fire a momentary trigger by its canonical path — a TriggerParameter, or a momentary boolean like a MacroTriggers macro (the pulse's rising edge fires any wired trigger modulations). The value auto-resets to false. If launch quantization defers the fire (pattern/clip launch), the response has pending=true and it fires at the next tempo boundary — do NOT re-fire, that queues a duplicate. Firing is an action with side effects, not undoable state; use set_parameter for toggles and values.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical LX path of the trigger parameter |

### `list_stages`

_read-only_

Every stage on a MultiStageEnvelope modulator (basis/value/shape point), in basis order: 0-based index, basis, value, per-segment shape (exponent applied to the segment arriving at this stage; 1 is linear), initial/last (true for the fixed first/last stage, at basis 0/1 — never removable, basis never moves). Stages are NOT LXComponents and have no canonical path — they don't appear in list_parameters and are addressed positionally as {path, index} to add_stage/remove_stage/set_stage. Indices are POSITIONAL: they shift whenever a stage is added or removed — re-list rather than reuse an index from an earlier response.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the MultiStageEnvelope modulator |

### `add_stage`

_mutating_

Insert an interior stage on a MultiStageEnvelope modulator. basis (rejected unless strictly between 0 and 1, and unless it differs from every existing stage's basis — landing on the fixed first/last stage's position, or on another interior stage's, would shadow it during interpolation instead of creating a distinct point) and value (rejected outside [0,1], the class's normalized output range) place the new point; the stage is inserted in basis order — its resulting index depends on where it lands among the existing stages, read it back from the response rather than assuming it was appended. shape (default 1, linear; rejected if negative — Math.pow(relativeBasis, shape) grows unbounded, and can hit Infinity, as relativeBasis approaches 0 near a segment's start; 0 is valid and produces an instant step to this stage's value) is the exponent applied to the segment's relative basis (value = lerp(prevValue, value, relativeBasis^shape)) — it does not map to convex/concave in a fixed way, since that also depends on whether the segment rises or falls; a shape below 1 front-loads the value's approach to this stage's value, above 1 back-loads it. Returns the created stage plus the envelope's resulting stageCount. Stage indices are POSITIONAL: every later stage shifts when one is added or removed — re-run list_stages rather than reuse an index from an earlier response. MultiStageEnvelope has no LXCommand for stage mutation, so this is a direct engine edit (marks the project dirty itself, since stages are saved into it). Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the MultiStageEnvelope modulator |
| `basis` | number | yes | — | Position along the envelope, strictly between 0 and 1 exclusive, and distinct from every existing stage's basis (rejected otherwise — a coinciding basis would shadow that stage) |
| `value` | number | yes | — | Envelope output value at this stage, normalized [0,1] (rejected outside that range) |
| `shape` | number | no | — | Exponent applied to relativeBasis^shape for the segment arriving at this stage (default 1, linear; rejected if negative — can drive the output to Infinity near the segment's start; 0 is a valid instant step; below 1 front-loads the approach to this stage's value, above 1 back-loads it) |

### `remove_stage`

_mutating_

Remove an interior stage from a MultiStageEnvelope modulator, addressed by {path, index} (index from list_stages). Only interior stages may be removed — the fixed first/last stage (basis 0/1, initial:true/last:true) is rejected with invalid_argument before anything changes. Returns the removed stage (same shape as list_stages) plus the envelope's resulting stages read back from the engine. Stage indices are POSITIONAL: every later stage shifts down after a removal — use the returned stages array or re-run list_stages rather than reuse an index from an earlier response. MultiStageEnvelope has no LXCommand for stage mutation, so this is a direct engine edit (marks the project dirty itself, since stages are saved into it). Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the MultiStageEnvelope modulator |
| `index` | integer | yes | 0–2147483647 | 0-based index of the stage in the envelope's stage list (from list_stages) |

### `set_stage`

_mutating_

Edit one existing stage on a MultiStageEnvelope modulator, addressed by {path, index} (index from list_stages). Applies any combination of basis (new position), value (in [0,1] normalized space — rejected outside that range, matching the class's normalized output), and shape (curve exponent of the segment arriving at this stage; rejected if negative — Math.pow(relativeBasis, shape) grows unbounded, and can hit Infinity, as relativeBasis approaches 0 near a segment's start; 0 is valid and produces an instant step; also rejected on the fixed initial stage, index 0 — it has no preceding segment, so its shape field is never read); at least one is required. On an interior stage, basis is rejected unless it lands strictly between its neighboring stages' basis values — a stage can never reach or cross a neighbor, since landing exactly on one would shadow it during interpolation (remove and re-add it to jump past one). On the fixed first/last stage (initial:true/last:true), basis never moves — value still applies. The payload echoes the stage read back from the engine (resulting basis/value/shape), never the request. Stage indices are POSITIONAL: they shift whenever a stage is added or removed — re-run list_stages rather than reuse an index from an earlier response. MultiStageEnvelope has no LXCommand for stage mutation, so this is a direct engine edit (marks the project dirty itself, since stages are saved into it). Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the MultiStageEnvelope modulator |
| `index` | integer | yes | 0–2147483647 | 0-based index of the stage in the envelope's stage list (from list_stages) |
| `basis` | number | no | — | New position: on an interior stage, rejected unless strictly between its neighboring stages' basis values (landing on a neighbor would shadow it); ignored on the fixed first/last stage |
| `value` | number | no | — | New envelope output value at this stage, normalized [0,1] (rejected outside that range) |
| `shape` | number | no | — | New exponent applied to relativeBasis^shape for the segment arriving at this stage (1 is linear; rejected if negative — can drive the output to Infinity near the segment's start; 0 is a valid instant step; below 1 front-loads the approach to this stage's value, above 1 back-loads it; rejected on the fixed initial stage, index 0, which has no preceding segment) |

## Palette & snapshots

### `save_swatch`

_mutating_

Capture the active swatch's current colors as a new saved swatch, appended to the end of get_palette's swatches list. Returns the new swatch's canonical path — pass it to set_swatch to recall it later, or move_swatch/remove_swatch to manage it. Undoable in Chromatik with Cmd-Z.

No parameters.

### `set_swatch`

_mutating_

Apply a saved swatch's colors onto the active swatch, by its canonical path (as returned by save_swatch or listed in get_palette's swatches). Same effective change as firing the swatch's recallPath with fire_trigger — including transitionEnabled/transitionTimeSecs interpolation — but undoable in Chromatik with Cmd-Z, unlike the trigger.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the saved swatch to apply, e.g. /lx/palette/swatches/swatch/1 |

### `remove_swatch`

_mutating_

Remove a saved swatch by its canonical path (as returned by save_swatch, or listed in get_palette's swatches). The active swatch's current colors are unaffected. Remaining swatches reindex afterwards, so held paths can go stale. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the saved swatch to remove, e.g. /lx/palette/swatches/swatch/1 |

### `move_swatch`

_mutating_

Move a saved swatch to a new 0-based index within get_palette's swatches list. Returns invalid_argument if the index is out of range. Other swatches reindex around it, so held paths can go stale. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the saved swatch to move, e.g. /lx/palette/swatches/swatch/1 |
| `index` | integer | yes | -2147483648–2147483647 | 0-based destination index within the saved swatch list |

### `add_color`

_mutating_

Add a color slot to a swatch, appended at the end — targets the active swatch (get_palette's activeSwatch) by default, or a saved swatch if swatch is given. Set the new color's hue/saturation/brightness via set_parameter on the returned primaryPath. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `swatch` | string | no | — | Optional canonical path of a saved swatch (as returned by save_swatch); defaults to the active swatch |

### `remove_color`

_mutating_

Remove the last color slot from a swatch — targets the active swatch (get_palette's activeSwatch) by default, or a saved swatch if swatch is given. A swatch's first color can never be removed (LX requires at least one); this returns invalid_argument on a single-color swatch. Remaining colors reindex, so held paths can go stale. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `swatch` | string | no | — | Optional canonical path of a saved swatch (as returned by save_swatch); defaults to the active swatch |

### `add_snapshot`

_mutating_

Capture the current mixer/pattern/effect/modulation state as a new snapshot, appended to the end of the list. The optional label overrides LX's default 'Snapshot-N' name. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `label` | string | no | — | Optional label for the new snapshot |

### `recall_snapshot`

_mutating_

Recall a snapshot's captured state, restoring the mixer/pattern/effect/modulation values it holds. By default the recall follows the snapshot engine's own transitionEnabled/transitionTimeSecs settings (see list_snapshots) — when transitions are enabled, values fade in over transitionTimeSecs; pass immediate=true to force an instant recall for this call regardless of that setting. The engine's recallMixer/recallPattern/recallEffect/recallModulation/recallMaster/recallOutput booleans (also from list_snapshots) limit which categories of state the recall is allowed to touch. It lands on the Chromatik undo stack, but Cmd-Z after a recall does not reliably restore plain parameter values to their pre-recall state (an LX-side ordering quirk in how it builds the undo entry) — recall_snapshot again, or another snapshot, to get back to a known state instead of relying on undo here.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the snapshot, as returned by add_snapshot/list_snapshots |
| `immediate` | boolean | no | — | Force an instant recall, bypassing transitionEnabled/transitionTimeSecs for this call; defaults to false (follow the engine's transition setting) |

### `update_snapshot`

_mutating_

Recapture the current mixer/pattern/effect/modulation state into an existing snapshot, overwriting its previously saved values. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the snapshot to update, as returned by add_snapshot/list_snapshots |

### `remove_snapshot`

_mutating_

Remove a snapshot by canonical path (as returned by add_snapshot/list_snapshots). Snapshots are addressed by a 1-based structural path (e.g. /lx/snapshots/snapshot/2) — remaining snapshots reindex afterwards, so held paths can go stale; re-list before reusing one. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the snapshot, as returned by add_snapshot/list_snapshots |

## Model views: spatial composition

Views are named subsets of the model ("Cube Interior", "Faces Exterior"), defined by a
tag selector; every channel, pattern, and effect has a `view` parameter that clips its
rendering to one. This is how one project paints different geometry with different
content.

Selectors are a small CSS-like language over model tags — space for descendant, `,`
union, `&` intersect, `;` separate groups, `*` group-by, `tag[n-m]` index ranges (full
grammar in the `get_views` tool description).

```
add_view {label: "Front+Back", selector: "cubeFrontExterior ; cubeBackExterior", orientation: "group"}
set_parameter {path: /lx/mixer/channel/1/pattern/1/view, value: "Front+Back"}
```

### `get_views`

_read-only_

Named model subsets ('views', at /lx/structure/views/view/<n>) that a device's 'view' selector can clip its rendering to. A device left on 'Default' inherits its view from its parent (effect -> pattern -> channel -> master -> whole model); assigning a named view clips that device to only the matched points. 'selector' matches model tags (see modelTags for the vocabulary this project's fixtures expose) with a small grammar: a bare tag ('cube') selects everything tagged with it; space is a descendant match ('cube face' = faces inside cubes); ',' unions ('cube, sphere'); '&' intersects with the preceding match ('cube & active'); '>' requires a direct child ('cube > face'); ';' separates independent groups within one selector; '*' groups by the left side and sub-selects within each group on the right ('cube * face'); and a tag can carry an index range in brackets — 'cube[0]', 'cube[2-5]', 'cube[even]', 'cube[odd]', 'cube[:2]' (every 2nd), 'cube[1:2]' (every 2nd starting at 1). 'numGroups'/'numFixtures' are live match feedback: they update automatically after editing 'selector' via set_parameter on its path, so re-read the view (or get_views again) to check whether an edited selector matched anything. Fire 'cuePath' (a momentary trigger, via fire_trigger) to preview a view in the Chromatik UI — only one view cues at a time. 'assignments' lists which devices currently reference each view (devices left on Default are omitted, since they aren't referencing any view definition).

No parameters.

### `add_view`

_mutating_

Compose a new named model subset ('view', at /lx/structure/views/view/<n>), matched by a tag selector — see get_views for the selector grammar and the modelTags vocabulary this project's fixtures expose. 'numFixtures'/'numGroups' in the response are immediate match feedback: 0 fixtures is legal (an empty view) but usually means a selector typo — cross-check against get_views' modelTags. Map a device to the new view by setting its 'view' parameter (set_parameter) to the view's label.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `label` | string | yes | — | Display label for the new view, e.g. 'Cubes' |
| `selector` | string | yes | — | Tag selector matched against the model, e.g. 'cube' or 'cube & active' — see get_views for the full grammar |
| `normalization` | string | no | one of: `relative`, `absolute` | Whether point coordinates renormalize to the view's own bounds ('relative', default) or keep the whole model's absolute bounds ('absolute') |
| `orientation` | string | no | one of: `global`, `group` | Whether view points orient in absolute/global space ('global', default) or relative to their matching group's own orientation ('group') |

### `remove_view`

_mutating_

Remove a view by its canonical path (as returned by add_view/get_views). Devices selecting a different, surviving view are unaffected. But a device whose 'view' selector pointed at the removed view is NOT reset to Default — LX only clamps the selector's stored index into the shrunk view list, so it silently reassigns to whichever view (or Default) now sits at that index. Re-check device 'view' assignments (get_views' assignments) after removing a view rather than assuming they reset — undo does not fix this trap either; remap affected devices to Default before removing a view they still reference.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the view to remove, e.g. /lx/structure/views/view/1 |

## Model & fixtures

`describe_model` walks the model tree those view selectors match against (depth-limited;
re-call with a child's path or a higher depth to keep descending) — its
`pointIndexRange` fields index the same global color buffer `get_frame` reports.
`list_fixtures`/`get_fixture` report the physical wiring layer beneath that model tree —
output protocol (universe/channel/host) and geometry transform, one entry per fixture.
`set_fixture_params` is the batched, undo-grouped way to set several fixture parameters at
once, and the only way to reach a JSON fixture's `.lxf`-declared `jsonParameters` (e.g.
controller IP strings), which have no canonical path. `set_fixture_tags` sets a fixture's
model tags (the `get_views` selector vocabulary) with pre-write validation. `reload_fixtures`
picks up `.lxf` edits made on disk — nothing does so automatically.

### `describe_model`

_read-only_

The model tree: the geometry hierarchy the rig renders onto, from the whole installation down to individual points. Each node reports 'tags' (the same vocabulary get_views' selectors match against — e.g. a selector 'cube' matches every node tagged 'cube'), 'size' (point count), 'pointIndexRange' ([firstIndex, lastIndex], the bounding range of this node's point indices in the same global color buffer get_frame reads back — NOT a claim that the node owns every index in that range; check 'contiguous' before slicing the buffer), 'contiguous' (true when the node's indices form an unbroken run equal to 'size', so the range is exactly its points; false when they're interleaved with other nodes' indices, as for a grid column submodel — both fields are omitted for an empty node), 'bounds'/'center' (spatial extent), and 'childCount' (how many submodels sit below, even when 'children' itself is absent). Pass 'path' (a model node path, as emitted in this tool's own 'path' field — not a component canonical path, since LXModel isn't addressable through the component tree) to describe a submodel instead of the whole installation; omit it for the root, which also reports 'modelName', 'isStatic', 'totalPoints', 'fixtureCount', and 'tagVocabulary' (every distinct tag in the project with its occurrence count). 'depth' (default 2, max 10) bounds how many levels of children are expanded below the addressed node — real installations can have thousands of submodels, so an unbounded dump would blow a client's context. When 'childCount' is nonzero but 'children' is missing, depth ran out; re-call with 'path' set to one of the reported child paths (or a higher 'depth') to keep descending.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Model node path (as emitted in this tool's own 'path' field); omit for the whole installation's root |
| `depth` | integer | no | — | Levels of children to expand below the addressed node (default 2, clamped to 10; must be >= 0) |

### `get_fixture_format`

_read-only_

Return the .lxf fixture-file JSON schema reference: top-level keys, component types (point/points/strip/arc/class/file-reference), the parameter + $expr expression system ($instance/$instances and instances-expansion), outputs and segments per protocol, and tag rules — with worked examples. Use this to author or understand a fixture file; pairs with reload_fixtures to pick up on-disk edits to an existing fixture, and get_fixture/describe_model to inspect a fixture's already-loaded structure.

No parameters.

### `list_available_fixtures`

_read-only_

What add_fixture can instantiate: 'classes' (built-in fixture types — pass the simple name, e.g. 'GridFixture', or the full class name, as add_fixture's 'class' argument) and 'jsonTypes' (fixtures loaded from a .lxf file in the Fixtures folder — pass the type string, e.g. 'MyRig/Cube', as add_fixture's 'type' argument; 'isVisible' false means the .lxf declares itself hidden from the add menu, e.g. a subfixture-only helper type). 'errors' lists any .lxf that failed to parse (syntax/I-O error) — its type is not addable until fixed. 'fixturesDirectory' is the absolute path .lxf files live in — write a new one there with your own file tools, then call this again (or reload_fixtures) to pick it up.

No parameters.

### `list_fixtures`

_read-only_

The fixture layer: the physical wiring beneath the model tree describe_model reports — each fixture's geometry transform, output protocol wiring, and (for a JsonFixture, loaded from a .lxf file) its load status. 'pointIndexRange' ([firstIndex, lastIndex]) indexes the same global color buffer get_frame and describe_model report against. Every fixture parameter is settable via set_parameter on '<path>/<param>' — e.g. '<path>/artNetUniverse', '<path>/x' — this is the primary way to configure a fixture's wiring and placement once it exists. Top-level 'outputError' reports universe/channel collisions LX detected between fixtures' output segments (empty when clean). 'output' is present only for a protocol-driven fixture (protocol 'NONE' when no output is configured); a JsonFixture's outputs are declared inside its .lxf file instead, so it has no 'output' key — see 'fixturePath'/'error'/'warnings' there instead. A fixture can itself contain subfixtures (e.g. a JsonFixture's .lxf 'components', recursively) — 'childCount' is the number of those subfixtures, while 'submodelCount' is the unrelated number of model-tree groupings the fixture's own geometry splits into (e.g. a GridFixture's per-row/per-column submodels; it has 0 subfixtures but several submodels). A deactivated fixture (see 'deactivate') has no built model until it is reactivated and the structure regenerates — for such a fixture 'modelAvailable' is reported as false (omitted, meaning true, otherwise), 'tags' falls back to the .lxf-declared subset only, and 'submodelCount' is 0. Subfixture paths (e.g. /lx/structure/fixture/1/fixture/3) are addressable with get_parameter/set_parameter exactly like top-level fixtures — writes to a subfixture of a JsonFixture are rejected, since its values are computed from the .lxf and recomputed on reload. Use get_fixture on a single fixture's path for its full parameter list, submodels, and subfixture tree (with a depth limit).

No parameters.

### `get_fixture`

_read-only_

One fixture's full detail: everything list_fixtures reports for it, plus 'parameters' (every parameter it owns — including type-specific ones like a GridFixture's numRows/numColumns or an ArcFixture's degrees — settable via set_parameter on its own path, same as any other component parameter), 'submodels' (the fixture's own child model nodes, e.g. a GridFixture's per-row and per-column groupings — each with path/tags/size/pointIndexRange/contiguous/metaData, same node shape as describe_model; empty when the fixture is deactivated and has no built model — see 'modelAvailable' in list_fixtures), 'children' (the fixture's subfixture tree — e.g. a JsonFixture's .lxf-declared 'components', recursively — depth-limited by the 'depth' argument; each node uses the same shape as a list_fixtures row, itself with a nested 'children' if depth allows further recursion; 'childCount' there is the number of direct subfixtures, distinct from 'submodelCount'), and for a JsonFixture, 'jsonParameters' (the knobs its .lxf file declares — these have no canonical path, so they carry no 'path' field here and are NOT reachable via set_parameter; set them by name via set_fixture_params). Subfixture paths (e.g. '<path>/fixture/3') are addressable with get_parameter/set_parameter/get_fixture exactly like top-level fixtures — writes to a subfixture of a JsonFixture are rejected, since its values are computed from the .lxf and recomputed on reload. 'depth' is silently clamped to its max (real installations can have hundreds of subfixtures nested deep) rather than erroring — only a negative depth is rejected.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the fixture, e.g. /lx/structure/fixture/1 |
| `depth` | integer | no | — | How many levels of subfixtures to include in 'children' (default 1, clamped to 10 max; negative is rejected). A real installation's fixture tree can be hundreds of nodes deep and wide (e.g. ~640 subfixtures on an Apotheneum-shaped rig), so this is capped rather than unbounded. |

### `get_output_map`

_read-only_

The output wiring beneath the fixture tree: for each fixture, its declared protocol/host/port/universe/channel/byteOrder plus a DERIVED channel footprint ('numChannels' — own point count times bytes-per-pixel) and an 'estimatedUniverseSpan' [startUniverse, endUniverse] for universe-based protocols (ARTNET/SACN/KINET) computed by rolling that footprint across universes the same way LX itself overflows (512 DMX channels per universe for ARTNET/SACN/KINET). 'pointIndexRange' indexes the same global point buffer describe_model/get_frame use. IMPORTANT: this map is DECLARED/DERIVED, NOT LX's resolved per-packet output allocation — LX keeps that privately (LXStructureOutput's generatedOutputs/Packet have no public accessor) — and the span estimate assumes one contiguous segment per fixture, ignoring serpentine wiring, segment stride, and cross-fixture packet packing, so it can diverge from LX's actual allocation on complex rigs. OPC/DDP fixtures get 'estimatedUniverseSpan: null' (those protocols use a data-length model, not universes). A fixture whose wiring is declared inside its .lxf file (e.g. a real installation's JsonFixture) reports 'directOutputCount' (its outputsDirect size) and an 'outputsNote' instead of a universe/channel estimate — LX exposes no public accessor for a .lxf-declared output's resolved universe, so none is fabricated. 'outputError' is LX's own collision report (lx.structure.outputError) — non-empty means LX itself detected an overlap; trust it over the estimate. Pairs with set_fixture_params: set artNetUniverse/dmxChannel/etc., then re-call this to check the resulting footprint. 'path' optional: map one fixture's subtree; omitted maps every top-level fixture.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Canonical path of a fixture to map (with its subfixture subtree); omit to map every top-level fixture |

### `add_fixture`

_mutating_

Instantiate a fixture (see list_available_fixtures for what's addable) — exactly one of 'class' (a built-in fixture type, e.g. GridFixture) or 'type' (a .lxf file's type string, e.g. MyRig/Cube) must be given. 'index' (0-based, clamped to the current fixture count) inserts at that position in lx.structure.fixtures instead of appending at the end — supported with 'class' only; 'type' always appends, and combining 'type' with 'index' is rejected (add the fixture, then reposition it with move_fixture). 'label' and 'params' (registered parameters only — x/y/z, artNetUniverse, a type-specific one like GridFixture's numRows, etc; NOT a JsonFixture's .lxf-declared parameters, which have no value until the fixture loads — configure those afterwards with set_fixture_params) are applied right after the add, and the whole call — instantiate plus configure — is a single undo step. Every fixture path is POSITIONAL (/lx/structure/fixture/N, 1-indexed) and shifts after any later add/remove/move — re-list with list_fixtures rather than reusing a path from an earlier response. Rejected when the structure is in static-model mode.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `class` | string | no | — | Built-in fixture class, simple or full name (see list_available_fixtures' 'classes'), e.g. 'GridFixture'. Exactly one of class/type is required. |
| `type` | string | no | — | A .lxf fixture type string (see list_available_fixtures' 'jsonTypes'), e.g. 'MyRig/Cube'. Exactly one of class/type is required. |
| `index` | integer | no | — | 0-based insert position in lx.structure.fixtures; omit to append at the end. Clamped into range. Supported with 'class' only — 'type' always appends, and 'type' + 'index' together is rejected. |
| `label` | string | no | — | Optional display label; overrides LX's default auto-suffixed label (e.g. 'Grid 2'). |
| `params` | object | no | — | Optional map of registered parameter name -> initial value, applied right after the add (folded into the same undo step). Value type must match the parameter: a number for numeric/discrete, a boolean for toggles, a string for text. |

### `remove_fixture`

_mutating_

Remove a fixture by its canonical path (as returned by list_fixtures/add_fixture). Undoable with Cmd-Z. Every remaining fixture's path is POSITIONAL (/lx/structure/fixture/N, 1-indexed) and shifts after this call — re-list (list_fixtures) rather than reuse a held path. Rejected when the structure is in static-model mode.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the fixture to remove, e.g. /lx/structure/fixture/1 |

### `move_fixture`

_mutating_

Reposition a fixture within lx.structure.fixtures using a 0-based 'index'. Returns invalid_argument if the index is out of range. Undoable with Cmd-Z. Every fixture's path is POSITIONAL (/lx/structure/fixture/N, 1-indexed) and shifts for this fixture and any it moved past — re-list (list_fixtures) rather than reuse a held path. Rejected when the structure is in static-model mode.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the fixture to move, e.g. /lx/structure/fixture/2 |
| `index` | integer | yes | — | 0-based target position in lx.structure.fixtures; must be in [0, fixtureCount - 1] |

### `duplicate_fixture`

_mutating_

Clone a fixture — geometry, output protocol wiring, and (for a JsonFixture) its .lxf-declared parameter values all copy over — in one call, matching the UI's duplicate action. The clone gets a fresh component id and its output-enabled flag is reset to off (never silently start transmitting a duplicate). 'index' defaults to right after the source fixture; explicit values are clamped into [0, fixtureCount]. Undoable with Cmd-Z. Every fixture's path is POSITIONAL (/lx/structure/fixture/N, 1-indexed) and shifts after this call — re-list (list_fixtures) rather than reuse a held path. Rejected when the structure is in static-model mode.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the fixture to duplicate, e.g. /lx/structure/fixture/1 |
| `index` | integer | no | — | 0-based insert position for the clone, clamped into [0, fixtureCount]; omit to insert right after the source fixture. |

### `set_fixture_params`

_mutating_

Set several of a fixture's parameters in one call — both its registered parameters (x/y/z/yaw/pitch/roll/scale, enabled, brightness, numPoints, artNetUniverse, host, and any type-specific ones, e.g. a GridFixture's numRows — otherwise settable one at a time via set_parameter) and, for a JsonFixture (a fixture loaded from a .lxf file), the knobs its 'parameters' block declares (e.g. controller IP strings, per-controller booleans, geometry floats) — these JSON parameters have no canonical path, so set_parameter cannot reach them; this tool is their only write path, addressed by name (see get_fixture's 'jsonParameters'). Every name is resolved and every value type-checked before anything is written — an unknown name or a type mismatch on any one entry leaves the fixture completely untouched, nothing partially applies. The WRITE itself is not atomic across a mixed numeric+string call, though: the numeric/boolean edits (batched into a single undo entry) are always performed before the string edits (one undo entry each, reported in 'undoEntries'), so if a string write fails partway through, earlier writes stay applied — and LX clears its entire undo/redo stack when any command fails, not just that entry. Batch related edits into one call rather than calling this repeatedly regardless. Each parameter change triggers a full model rebuild (re-point, re-normalize, rebuild every view, plus a synchronous System.gc()), and a JSON parameter write additionally re-reads the fixture's .lxf from disk — another reason to batch. Never drive a continuous control (e.g. an LFO) into a fixture parameter this way; it is metrics/placement/tag data, not a render input. Rejected on a subfixture of a JsonFixture (its values are computed from the .lxf and recomputed on reload — edit the .lxf and call reload_fixtures instead); a top-level .lxf fixture's own parameters (registered or JSON) are the intended edit surface. Registered parameters are resolved before same-named .lxf-declared ones — a .lxf may legally declare a parameter with the same name as a registered one (e.g. 'scale'), in which case the registered parameter is written and the JSON one is left untouched; such names are reported in 'shadowedJsonParams' (present only when non-empty).

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the fixture, e.g. /lx/structure/fixture/1 |
| `params` | object | yes | — | Map of parameter name -> new value. Registered parameters are looked up first, then (for a JsonFixture) its .lxf-declared parameters by name. Value type must match the parameter: a number for numeric/discrete, a boolean for toggles, a string for text. |

### `set_fixture_tags`

_mutating_

Set a fixture's model tags — the vocabulary get_views' selectors match against (see get_views). Replaces the fixture's whole tag list. Every token is validated against LX's tag regex ([A-Za-z0-9_.\-/]+) before anything is written: LX itself silently drops any tag that fails this check (and silently restores the fixture's *default* tags if every token fails), so an unvalidated write can look successful while quietly breaking view addressing — this tool rejects the whole call instead, naming the offending token, and writes nothing. Returns the resulting tag list so the caller sees what actually landed. Rejected on a subfixture of a JsonFixture, same as set_fixture_params.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the fixture, e.g. /lx/structure/fixture/1 |
| `tags` | array<string> | yes | — | The fixture's new complete tag list — replaces the existing tags. |

### `reload_fixtures`

_mutating_

Pick up .lxf fixture files edited on disk with your own file tools — nothing watches the Fixtures folder, so a .lxf edit is otherwise invisible until this is called. Two steps: re-walks the Fixtures folder to refresh the available fixture type list ('jsonTypes'/'errors'), then reloads every instantiated top-level JsonFixture from its .lxf and regenerates the model exactly once — the only batched regeneration path in LX, so this is cheaper than N individual set_fixture_params calls. Also the only way to pick up a changed fixture *type* on a live fixture (changing its type otherwise has no effect, since loading only happens once). A JSON parameter's value survives the reload only if a parameter of the same name still exists in the new .lxf; otherwise it reverts to the file's declared default. Not undoable. Returns the refreshed type list plus every fixture's error/errorMessage/warnings after the reload, so failures in the edited file are visible immediately.

No parameters.

## MIDI

Read-side only for now: devices (inputs/outputs), the parameter mappings incoming
MIDI drives, and connected control surfaces. Ports/mappings/surfaces carry no
canonical path, so each is addressed by its 0-based list index — re-list before
reusing one, since indices shift when the underlying list changes.

### `list_midi_devices`

_read-only_

List the MIDI input and output ports LX has discovered. Each input carries three independent routing flags: channelEnabled (notes/CCs forwarded to channel and modulator devices), controlEnabled (events feed the control-mapping layer — see list_midi_mappings), and syncEnabled (this port's MIDI clock drives the engine tempo, effective only when get_tempo reports clockSource MIDI). enabled is the union of those three. connected starts true and flips false only if the device disconnects mid-session; ports remembered from the project file whose hardware is absent are not listed at all. Ports are addressed by their 0-based index (they carry no canonical path); indices shift as devices connect or disconnect, so re-list before reusing one.

No parameters.

### `list_midi_mappings`

_read-only_

List the parameter mappings driven by incoming MIDI. Each entry gives type ('note' or 'cc'), the 0-based MIDI channel (0-15), number (note pitch or CC number, 0-127), a note-name for note mappings, and targetPath — the canonical path of the mapped parameter, usable with get_parameter/set_parameter. Mappings are addressed by their 0-based index; indices shift when a mapping is removed, so re-list before reusing one. Only inputs with controlEnabled (see list_midi_devices) actually apply these mappings. label is LX's description of the mapping source (for note mappings this duplicates the note name); targetLabel is the mapped parameter's display label.

No parameters.

### `list_midi_surfaces`

_read-only_

List the instantiated MIDI control surfaces (e.g. an APC40, a MidiFighterTwister) — a surface is a two-way hardware controller LX drives with a dedicated protocol, distinct from the ad-hoc parameter mappings in list_midi_mappings. Each entry gives the surface name, the deviceName it binds to, enabled (actively driving the hardware) and connected (device present). Surfaces are addressed by their 0-based index. A registered surface only appears here once its device has been seen; surfaces LX knows how to drive but hasn't instantiated are not listed.

No parameters.

### `list_midi_templates`

_read-only_

List the MIDI templates instantiated in this project. Templates expose named hardware controls as ordinary parameters at paths such as /lx/midi/template/1/knob-A1, which can be inspected with list_parameters and used with wire_modulator or wire_trigger. Each entry includes its canonical path, registered class, expected device name, selected source/output, and connection state. The 0-based index and 1-based path may shift when templates are removed or reordered, so re-list before reusing them.

No parameters.

### `add_midi_template`

_mutating_

Add a registered MIDI template to the project. Pass its full or simple class name, template name, or expected MIDI device name as 'class' — for example heronarts.lx.midi.template.AkaiMPD218, AkaiMPD218, Akai MPD218, or MPD218. LX automatically selects a matching connected input/output when available. Returns the new template in list_midi_templates' shape; inspect its path with list_parameters to discover controls for wire_modulator/wire_trigger. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `class` | string | yes | — | Registered template class, template name, or MIDI device name (e.g. AkaiMPD218, Akai MPD218, or MPD218) |

### `add_midi_mapping`

_mutating_

Add a MIDI mapping: incoming note-on or control-change messages on a channel drive a parameter, resolved by its canonical LX path (see list_parameters). type is 'note' (number is the pitch, 0-127) or 'cc' (number is the CC number, 0-127); channel is 0-based (0-15). The mapping fires on channel+pitch/cc identity, not a specific velocity/value — the actual incoming velocity/value still reaches the parameter at runtime. Only parameters that support MIDI mapping (most numeric/bounded/toggle/discrete ones) can be targeted; aggregate parameters (color, MIDI filter) are rejected — map their component paths instead. Returns the created mapping in list_midi_mappings' shape, including its 0-based index; that index shifts if other mappings are later removed, so re-list before reusing it. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `type` | string | yes | one of: `note`, `cc` | Mapping type: 'note' (note-on) or 'cc' (control change) |
| `channel` | integer | yes | 0–15 | 0-based MIDI channel (0-15) |
| `number` | integer | yes | 0–127 | Note pitch or CC number, 0-127 depending on type |
| `targetPath` | string | yes | — | Canonical LX path of the parameter to map, as returned by the list/get tools |

### `remove_midi_mapping`

_mutating_

Remove a MIDI mapping by its 0-based index into list_midi_mappings. Returns the removed mapping's summary. Remaining mappings reindex afterwards, so held indices go stale — re-list before reusing one. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `index` | integer | yes | -2147483648–2147483647 | 0-based index of the mapping to remove, as returned by list_midi_mappings |

### `set_midi_input`

_mutating_

Set one or more of a MIDI input's routing flags by its 0-based index into list_midi_devices' inputs list: channelEnabled (forward notes/CCs to channel and modulator devices), controlEnabled (feed the control-mapping layer — see list_midi_mappings), syncEnabled (this port's MIDI clock drives the engine tempo). At least one flag must be provided; flags left unset are unchanged. enabled is derived (the union of the three, though a bound control surface can also hold the port open) and cannot be set directly. Returns the updated input in list_midi_devices' shape. Not undoable — LX has no undo command for these flags.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `index` | integer | yes | -2147483648–2147483647 | 0-based index of the input, as returned by list_midi_devices |
| `channelEnabled` | boolean | no | — | Forward notes/CCs from this input to channel and modulator devices |
| `controlEnabled` | boolean | no | — | Feed events from this input to the control-mapping layer |
| `syncEnabled` | boolean | no | — | Let this input's MIDI clock drive the engine tempo |

### `set_midi_surface_enabled`

_mutating_

Enable or disable a control surface by its 0-based index into list_midi_surfaces. Returns the updated surface in list_midi_surfaces' shape. Not undoable — LX has no undo command for surface enablement.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `index` | integer | yes | -2147483648–2147483647 | 0-based index of the surface, as returned by list_midi_surfaces |
| `enabled` | boolean | yes | — | Whether the surface should be enabled |

## Composition: the arrange timeline

The arrange-window composition at `/lx/timeline/composition` — transport, markers,
locators, lane lifecycle, audio/text lanes, and the record-arm. (Automation events
within a lane have their own section below.) Timeline
positions travel as **cursor objects**: reads always emit the full
`{millis, beatCount, beatBasis, formatted}`; writes take exactly one of
`{millis}` | `{beatCount[, beatBasis]}` | `{bars[, beats, sixteenths]}` (1-indexed) |
`{at: <origin>[, offsetBeats | offsetMillis]}`. The clip's `timeBase` decides which
fields are authoritative — under `TEMPO`, millis are derived from the clip's fixed
`referenceBpm`, not the live tempo. Lane paths (`.../lane/<n>`) and event indices are
positional and shift under edits: re-list rather than reuse them. Clip behavior
parameters (`timeBase`, `loop`, `referenceBpm`, `/lx/timeline/sync`) are ordinary
registered parameters for `set_parameter`; marker positions are not.

### `get_composition`

_read-only_

The arrange-timeline composition at /lx/timeline/composition: timeBase (ABSOLUTE or TEMPO — decides which cursor fields are authoritative), referenceBpm (the fixed bpm cursors' millis fields are derived from — NOT the live tempo), length/loopStart/loopEnd/playStart/playEnd/insertMarker markers, playhead, running, hasContent, armed (the timeline record-arm — a bare engine field with no canonical path, deliberately unreachable via set_parameter), sync, locatorCount, and a summary of every lane (see list_clip_lanes for the per-lane fields). Every cursor is the full object {millis, beatCount, beatBasis, formatted}; formatted is display-only, and under TEMPO timeBase the beat fields are authoritative while millis is derived via referenceBpm. Clip behavior parameters (timeBase, loop, referenceBpm, /lx/timeline/sync, …) are registered parameters — read/write them with list_parameters/set_parameter on the composition path; marker positions are NOT settable that way. Grid clips (/lx/mixer/channel/N/clip/M) share this envelope shape via their own tools.

No parameters.

### `get_clip`

_read-only_

One clip's timeline envelope: timeBase (ABSOLUTE or TEMPO — decides which cursor fields are authoritative), referenceBpm (the fixed bpm cursor millis are derived from — NOT the live tempo), the length/loopStart/loopEnd/playStart/playEnd/insertMarker markers, loop flag, playhead, running, pending (a quantized launch is scheduled but hasn't fired), hasContent, and laneCount. Every cursor is the full object {millis, beatCount, beatBasis, formatted}; formatted is display-only, and under TEMPO timeBase the beat fields are authoritative. path defaults to the arrange composition (/lx/timeline/composition) and also accepts a grid clip (/lx/mixer/channel/N/clip/M). Marker positions are set with set_clip_marker (NOT set_parameter); lane details come from list_clip_lanes; the composition's extra state (arm, sync, locators) comes from get_composition.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Canonical path of the clip — the composition (default: /lx/timeline/composition) or a grid clip (/lx/mixer/channel/N/clip/M) |

### `list_clip_lanes`

_read-only_

Every automation lane on a clip: canonical path (always <clipPath>/lane/<n>, 1-indexed — the address every lane tool takes), 0-based index, type (parameter \| pattern \| midiNote \| bus \| globalModulation \| colorPalette \| audio \| textNote), label, eventCount, uiVisible, removable, and the lane's target where it has one (parameterPath/busPath/channelPath). path defaults to the arrange composition (/lx/timeline/composition) and also accepts a grid clip (/lx/mixer/channel/N/clip/M). Lane paths are POSITIONAL: they shift whenever lanes are added, removed, or moved — and removing a modulator can cascade-remove its composition lanes — so re-list rather than reuse a path from an earlier response. removable:false marks auto-managed lanes (bus, global modulation, color palette, and a grid clip's MIDI/pattern lanes) that must not be removed. Event addressing within a lane is {lanePath, index} with index the absolute 0-based position in the lane's event list.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Canonical path of the clip — the composition (default: /lx/timeline/composition) or a grid clip (/lx/mixer/channel/N/clip/M) |

### `add_clip`

_mutating_

Create a clip in an empty grid slot — the verb that brings a slot into being so every other clip tool can address it. containerPath is the bus that owns the row (a channel like /lx/mixer/channel/1, or /lx/mixer/master); index is the 0-based scene row, and the resulting clip path is 1-indexed, so index 0 becomes /lx/mixer/channel/1/clip/1. An occupied slot is an invalid_argument naming the existing clip unless replace:true is passed (the save_project overwrite precedent) — replacing discards the old clip's automation and snapshot in a single undo step. An index at or past the engine's numScenes is rejected: LX hides such clips from the grid and from launch_scene, so raise /lx/clips/numScenes with set_parameter first. snapshot (default true) makes the clip recall a snapshot when launched, and LX captures the bus's live state into it right then — so add_clip with snapshot:true is already a capture of that moment, and capture_clip is how you overwrite it later. With snapshot:false the clip stores nothing (snapshotViewCount 0) until capture_clip runs. Either way a new clip has no automation content. Undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `containerPath` | string | yes | — | Canonical path of the bus that owns the grid row — a channel (/lx/mixer/channel/N) or /lx/mixer/master |
| `index` | integer | yes | 0–2147483647 | 0-based scene row; the clip's path is 1-indexed (index 0 -> .../clip/1). Must be below the engine's /lx/clips/numScenes |
| `snapshot` | boolean | no | — | Whether launching the clip recalls its snapshot (default true) — capture_clip writes that snapshot |
| `replace` | boolean | no | — | Overwrite a clip already in the slot (default false); without it an occupied slot is rejected |
| `label` | string | no | — | Optional label for the new clip; defaults to LX's <bus>-<row> naming |

### `remove_clip`

_mutating_

Remove a grid clip, emptying its slot — its automation lanes, notes, and snapshot go with it. path is a grid clip (/lx/mixer/channel/N/clip/M); the arrange composition (/lx/timeline/composition) is not removable and is rejected. Slots do NOT reindex: removing .../clip/2 leaves .../clip/1 and .../clip/3 where they were, because a grid row is an address, not a list position. Returns the shared remove-tool shape (removed: the clip's path, kind: "clip") plus the freed slot's containerPath, index, and the label the clip had. Undoable with Cmd-Z, which restores the clip's full contents.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the grid clip to remove, e.g. /lx/mixer/channel/1/clip/1 |

### `capture_clip`

_mutating_

Capture the current live state into a clip's snapshot, overwriting whatever it held — the write side of the snapshot that launch_clip mode 'launch' recalls. This is how a clip becomes a preset without placing automation points one at a time. A clip snapshot is BUS-SCOPED, not a whole-show capture: it stores the owning bus's active pattern (or every enabled pattern in blend mode), those patterns' parameters, and its effects. It does NOT store the channel fader, crossfade group, or composite mode — for a whole-mixer capture use add_snapshot/update_snapshot instead, and for a fader use an automation lane. Recall is gated on the clip's snapshotEnabled, so capturing into a clip with it off silently produces a snapshot that never fires — this turns the flag on instead and reports enabledRecall:true when it did, which costs a second Cmd-Z to undo. Returns the clip state read back after the capture; snapshotViewCount is how many parameter values were stored. path must be a grid clip (/lx/mixer/channel/N/clip/M) — the arrange composition has no owning bus to scope a capture to and is rejected. Undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the clip to capture into, e.g. /lx/mixer/channel/1/clip/1 |

### `launch_clip`

_mutating_

Start clip playback. mode 'play' (the default) is immediate and unquantized, from the 'from' cursor or the current playhead — it requires the clip to have content (a fresh composition has none until something is recorded or playEnd is pushed out with set_clip_marker) and to not already be running. mode 'automation' launches automation playback subject to the global launch quantization, from 'from' or the playStart marker — when quantization is set the response shows pending:true and running flips on the quantization boundary. mode 'launch' is the full quantized grid-style launch from playStart, which also recalls the clip's snapshot if enabled; it does not accept 'from'. path defaults to the arrange composition (/lx/timeline/composition) and also accepts a grid clip (/lx/mixer/channel/N/clip/M). Returns the clip state read back after the call (running, pending, playhead). Transport is not an LXCommand upstream — Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Canonical path of the clip — the composition (default: /lx/timeline/composition) or a grid clip (/lx/mixer/channel/N/clip/M) |
| `mode` | string | no | one of: `play`, `automation`, `launch` | play = immediate unquantized playback from 'from' or the playhead (default); automation = quantized automation launch from 'from' or playStart; launch = quantized grid-style launch from playStart with snapshot recall |
| `from` | object | no | — | Position to start playback from (play/automation modes only). Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |

### `stop_clip`

_mutating_

Stop clip playback immediately, bypassing any launch-quantization delay; also cancels a pending quantized launch. Safe to call on a stopped clip (no-op). path defaults to the arrange composition (/lx/timeline/composition) and also accepts a grid clip (/lx/mixer/channel/N/clip/M). Returns the clip state read back after the call (running, pending, playhead — the playhead stays where playback halted). Transport is not an LXCommand upstream — Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Canonical path of the clip — the composition (default: /lx/timeline/composition) or a grid clip (/lx/mixer/channel/N/clip/M) |

### `launch_scene`

_mutating_

Fire a whole row of the clip grid at once — every clip at that index across all channels plus the master bus. This is what makes a chapter land simultaneously; launching its clips one at a time loses that. index is the 0-based scene row, matching add_clip (index 0 fires the clips at /lx/mixer/channel/N/clip/1). By default the launch is subject to the global launch quantization — clips come back pending:true and flip to running on the quantization boundary — while immediate:true fires now. Each launched clip recalls its own snapshot if enabled (see capture_clip). A row no bus holds a clip on is rejected with invalid_argument rather than silently cancelled, which is what LX does on its own. Returns every clip on the row with its running/pending state read back after the call. A quantized launch also cancels any other scene still pending; immediate:true does NOT — it fires the clips directly, so a scene pending from an earlier quantized launch still lands afterwards. Transport is not an LXCommand upstream — Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `index` | integer | yes | 0–2147483647 | 0-based scene row to launch; must be below the engine's /lx/clips/numScenes |
| `immediate` | boolean | no | — | Fire now, bypassing the global launch quantization (default false) |

### `set_clip_marker`

_mutating_

Set or nudge one timeline marker on a clip: insertMarker (the scrub/insert position — this IS how you scrub the arrange timeline), loopStart, loopBrace (moves the whole loop, preserving its length; echoes the resulting loop start), loopEnd, loopLength, playStart, playEnd (pushing playEnd past the current length grows the clip and gives a fresh composition its timeline), or truncate (sets the clip length directly, rebounding the insert marker into range). Exactly one of cursor (absolute target), moveBeats, or moveMillis (signed relative nudge; negative moves earlier, bounded at the clip start). Every marker setter silently clamps to its legal range — the returned cursor is read back from the engine after the mutation and is the truth; clamped (absolute form only) reports whether it differs from the request. The full clip envelope is returned because markers are coupled (loop markers move together, playEnd can grow length). path defaults to the arrange composition (/lx/timeline/composition) and also accepts a grid clip (/lx/mixer/channel/N/clip/M). Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Canonical path of the clip — the composition (default: /lx/timeline/composition) or a grid clip (/lx/mixer/channel/N/clip/M) |
| `marker` | string | yes | one of: `insertMarker`, `loopStart`, `loopBrace`, `loopEnd`, `loopLength`, `playStart`, `playEnd`, `truncate` | Which marker to move; truncate sets the clip length |
| `cursor` | object | no | — | Absolute target position for the marker. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `moveBeats` | number | no | — | Signed relative nudge in beats (fractions allowed) — alternative to cursor |
| `moveMillis` | number | no | — | Signed relative nudge in milliseconds — alternative to cursor |

### `list_locators`

_read-only_

Every locator (named position marker) on the arrange-timeline composition, in timeline order: canonical path (/lx/timeline/composition/locator/<n>), 1-indexed index, label, and position as a full cursor object {millis, beatCount, beatBasis, formatted}. Locator addressing is 1-indexed everywhere — these tools, the locator:<n> cursor origin, and the canonical path — unlike lane/event payloads whose index is 0-based. Indices are POSITIONAL: the list re-sorts by cursor on every add or move and shifts on remove, so re-list rather than reuse an index from an earlier response. Locators may sit past the composition length. Labels are set at add_locator or renamed via set_parameter on <locatorPath>/label.

No parameters.

### `add_locator`

_mutating_

Adds a locator (named position marker) to the arrange-timeline composition at the given cursor, optionally labeled. Returns the new locator's summary {path, index, label, cursor} with the cursor read back from the engine. Locator positions are not clamped — a locator may sit past the composition length. The locator list re-sorts by cursor on every add or move, so the returned 1-indexed index is the new locator's position in timeline order and EARLIER locators' indices may have shifted — re-run list_locators rather than reuse indices from earlier responses. Undo removes the locator; a redo restores it unlabeled (the label is applied outside the undo stack).

| param | type | required | constraints | description |
|---|---|---|---|---|
| `cursor` | object | yes | — | Position for the new locator on the composition timeline Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `label` | string | no | — | Optional display label for the locator (rename later via set_parameter on <locatorPath>/label) |

### `remove_locator`

_mutating_

Removes a locator from the arrange-timeline composition, addressed by exactly one of 1-indexed index or exact label (which must be unambiguous — duplicate labels require the index). Returns the removed locator's last state {index, label, cursor} and the remaining locatorCount. Locator indices are POSITIONAL and shift on every add, move, or remove — re-run list_locators rather than reuse an index from an earlier response. Undo restores the locator with its label and position.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `index` | integer | no | 1–2147483647 | 1-indexed locator position in timeline order (see list_locators); exactly one of index or label |
| `label` | string | no | — | Exact locator label; must match exactly one locator |

### `move_locator`

_mutating_

Moves a locator on the arrange-timeline composition to a new cursor position. Address by exactly one of 1-indexed index or exact label (which must be unambiguous — duplicate labels require the index). Returns the locator's summary {path, index, label, cursor} read back from the engine: the list re-sorts by cursor on every move, so the returned index is the locator's NEW position in timeline order and other locators' indices may have shifted — re-run list_locators rather than reuse indices from earlier responses. Positions are not clamped; a locator may sit past the composition length.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `index` | integer | no | 1–2147483647 | 1-indexed locator position in timeline order (see list_locators); exactly one of index or label |
| `label` | string | no | — | Exact locator label; must match exactly one locator |
| `cursor` | object | yes | — | New position for the locator Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |

### `go_locator`

_mutating_

Transport jump to a locator on the arrange-timeline composition, addressed by exactly one of 1-indexed index or exact label (which must be unambiguous — duplicate labels require the index). Mirrors the app's own locator navigation: if the composition is RUNNING, relaunches automation playback from the locator (subject to global launch quantization); if STOPPED, moves the insert marker there and scrubs lane values to that point WITHOUT starting playback (launch separately to play). Returns the locator summary, launched (whether the running-relaunch branch was taken), running, and the insertMarker and playhead cursors read back from the engine — the insert marker is bounded to the composition length, so it may differ from a locator sitting past the end. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `index` | integer | no | 1–2147483647 | 1-indexed locator position in timeline order (see list_locators); exactly one of index or label |
| `label` | string | no | — | Exact locator label; must match exactly one locator |

### `add_clip_lane`

_mutating_

Add an automation lane to a clip. kind 'parameter': targetPath is a normalized parameter (e.g. /lx/mixer/channel/1/fader) and the lane records that parameter. kind 'pattern': targetPath is a pattern container — a channel like /lx/mixer/channel/1 — and the lane records its pattern changes. path defaults to the arrange composition (/lx/timeline/composition) and also accepts a grid clip (/lx/mixer/channel/N/clip/M). Idempotent: if the lane already exists nothing changes and the response is the existing lane with alreadyExisted:true. Returns the lane summary read back from the engine (its path/index show where the clip actually placed it) plus laneCount. Lane paths and indices are POSITIONAL: they shift whenever lanes are added, removed, or moved — and remove_modulator cascade-removes lanes recorded against the removed modulator's parameters — so re-run list_clip_lanes rather than reuse addresses from earlier responses. Undoable with Cmd-Z when a lane was created.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | no | — | Canonical path of the clip — the composition (default: /lx/timeline/composition) or a grid clip (/lx/mixer/channel/N/clip/M) |
| `kind` | string | yes | one of: `parameter`, `pattern` | Lane kind: 'parameter' (automate one parameter) or 'pattern' (record a channel's pattern changes) |
| `targetPath` | string | yes | — | What the lane records: a normalized parameter path for kind 'parameter', or a channel path for kind 'pattern' |

### `remove_clip_lane`

_mutating_

Remove an automation lane from its clip. path is the canonical lane address (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes. Only lanes reported removable:true may be removed: auto-managed lanes (bus, globalModulation, colorPalette everywhere; midiNote/pattern lanes on grid clips) are structural and rejected with invalid_argument before anything changes. Returns the removed lane's index/type/label plus the clip's resulting lane list read back from the engine. Lane paths and indices are POSITIONAL: every surviving lane after the removed one shifts down (and remove_modulator cascade-removes lanes on its own) — use the returned lanes array or re-run list_clip_lanes rather than reuse addresses from earlier responses. Undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical lane path from list_clip_lanes, e.g. /lx/timeline/composition/lane/4 |

### `move_clip_lane`

_mutating_

Move an automation lane to a new 0-based index within its clip. path is the canonical lane address (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes. The engine may override the request without failing: on the composition, parameter/midiNote/pattern lanes are constrained to their channel's section and section lanes (audio, textNote, globalModulation, colorPalette) snap across whole sections — the response's lane.index is the ACTUAL position read back from the engine, requestedIndex echoes the ask, and moved is false when the lane ended up where it started. Bus lanes mirror mixer order and are rejected — reorder the channel in the mixer instead. Lane paths and indices are POSITIONAL: every lane crossed by the move shifts, so re-run list_clip_lanes rather than reuse addresses from earlier responses. Undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical lane path from list_clip_lanes, e.g. /lx/timeline/composition/lane/4 |
| `index` | integer | yes | 0–2147483647 | 0-based destination index within the clip's lane list; the engine may constrain it — check the returned lane.index |

### `set_clip_lane_visible`

_mutating_

Show or hide an automation lane in the arrange/clip editor UI. Editor-only: a hidden lane still plays back. path is the canonical lane address (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes; lane paths are positional, so re-list rather than reuse one from an earlier response. Returns the lane summary with uiVisible read back from the engine. This is a direct engine edit (uiVisible has no command history). Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical lane path from list_clip_lanes, e.g. /lx/timeline/composition/lane/4 |
| `visible` | boolean | yes | — | true to show the lane in the editor, false to hide it |

### `add_audio_lane`

_mutating_

Add an audio lane to the arrange composition (/lx/timeline/composition), loading an audio file from an absolute path on the Chromatik machine (WAV/AIFF — whatever javax.sound.sampled reads; MP3 is not supported). The new lane lands at the TOP of the lane list (index 0), shifting every other lane's index — the returned laneCount shows the new lane total — the composition length grows to at least the audio length, and an empty composition gets its timeline enabled. Returns the shared lane-creation envelope {clipPath, lane, laneCount} plus the audio event {index, cursor, fileName, sourceLengthMs, length, end, filePath} and the composition's resulting length. The lane's enabled/gain are registered parameters — use set_parameter on the lane path. Lane paths are positional: they shift whenever lanes are added, removed, or moved, so re-run list_clip_lanes rather than reuse a path from an earlier response. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `file` | string | yes | — | Absolute path of the audio file on the machine running Chromatik (WAV/AIFF; rejected with invalid_argument if missing or unreadable) |

### `add_notes_lane`

_mutating_

Add a text-notes lane to the arrange composition (/lx/timeline/composition): a lane of timestamped annotation events (section names, cues, TODOs) that never affects playback. The lane is appended at the end of the lane list; pass an optional label to name it — multiple notes lanes are allowed and otherwise indistinguishable. Returns {clipPath, lane, laneCount} — the same envelope as the other lane-creating tools; add events with add_clip_note. Lane paths are positional: they shift whenever lanes are added, removed, or moved, so re-run list_clip_lanes rather than reuse a path from an earlier response. Undoable in Chromatik with Cmd-Z (undo removes the lane; the optional label rename is not a separate undo step).

| param | type | required | constraints | description |
|---|---|---|---|---|
| `label` | string | no | — | Optional display label for the lane (default "Notes") |

### `set_composition_arm`

_mutating_

Set the arrange timeline's record-arm. The arm flag is a bare engine field with no canonical path (/lx/timeline/arm deliberately does not resolve), so this is its only write path — set_parameter cannot reach it. Arming while the composition is stopped immediately launches it into recording: from the start when the composition is empty, from the playhead when it has content (upstream LX behavior). Disarming does NOT stop a running composition — use stop_clip. Returns armed and running read back from the engine. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `armed` | boolean | yes | — | true to arm the timeline for recording (may start the composition — see tool description), false to disarm |

## Composition events: automation points & ranges

Automation events have no canonical path — an event's address is the pair
`{lanePath, index}`, where `index` is its absolute 0-based position in the lane's
event list, and it shifts on every insert or remove. Never carry an index across a
mutation: re-read the lane with `get_clip_lane`, or pass `atCursor` on event-editing
tools to fail safely if the event moved. Point moves silently clamp between the
neighboring events and the clip bounds (a point can never cross a neighbor — remove
and re-insert to leapfrog), so always trust the echoed `cursor`/`value` in a payload
over what you sent. Range tools are lane-scoped — LX has no clip-wide range command —
and an empty range is a benign success with `removedCount: 0` that puts nothing on
the undo stack.

### `get_clip_lane`

_read-only_

One automation lane in full: the lane summary (as in list_clip_lanes) plus a paged read of its events. path is the lane address from list_clip_lanes (<clipPath>/lane/<n>, 1-indexed) and works on every lane type. Each event carries its ABSOLUTE 0-based index in the lane's event list — the address every event mutation takes — its cursor, and type-appropriate fields: parameter events have normalized/curve/shape, pattern events patternLabel/patternPath, MIDI events noteOn/pitch/velocity/midiChannel, audio events fileName/sourceLengthMs/length/end, text notes note/length/end. from/to are an INCLUSIVE cursor window; offset (default 0) indexes into the matched set and limit caps the page (default 200, max 1000). The envelope reports eventCount (lane total), total (matched by from/to), returned, and truncated (more matches exist past this page — advance offset). Event indices are positional and shift on every insert or remove — re-read the lane rather than reuse an index from an earlier response; the event-editing tools (set_automation_point, remove_automation_point, set_clip_note) take an atCursor guard to fail safely if it moved.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical lane path from list_clip_lanes: <clipPath>/lane/<n> (1-indexed), e.g. /lx/timeline/composition/lane/4 or /lx/mixer/channel/1/clip/1/lane/2. Lane paths are positional — re-list after lane mutations. |
| `from` | object | no | — | Only events at or after this position are matched (inclusive). Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `to` | object | no | — | Only events at or before this position are matched (inclusive). Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `offset` | integer | no | 0–2147483647 | 0-based offset into the MATCHED events (after from/to filtering), default 0 |
| `limit` | integer | no | 1–1000 | Maximum events to return, default 200 |

### `add_automation_point`

_mutating_

Insert an automation point on a parameter clip lane (undoable). lanePath is the lane address from list_clip_lanes (<clipPath>/lane/<n>) and must be a parameter-type lane — create one first with add_clip_lane if needed. normalized is the value in [0,1] normalized space; the engine clamps it (boolean lanes snap to 0/1, trigger lanes fire on 1.0), so the payload echoes the stored normalized, the cursor, and curve/shape read back from the created event, plus its resulting absolute index and the lane's new eventCount. Event indices are positional and shift on every insert or remove — re-read the lane rather than reuse an index from an earlier response; the event-editing tools (set_automation_point, remove_automation_point, set_clip_note) take an atCursor guard to fail safely if it moved.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `lanePath` | string | yes | — | Canonical path of a parameter clip lane, e.g. /lx/timeline/composition/lane/4 (see list_clip_lanes) |
| `cursor` | object | yes | — | Timeline position for the new point. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `normalized` | number | yes | — | Value at this point in [0,1] normalized space (clamped by the engine; boolean lanes snap to 0/1) |

### `set_automation_point`

_mutating_

Edit one existing automation point on a parameter clip lane, addressed by {lanePath, index} (lanePath from list_clip_lanes, type 'parameter'; index is the absolute 0-based position in the lane's event list). Applies any combination of: cursor (move the point in time), normalized (new value in [0,1] normalized space, not the raw parameter value — on a boolean parameter's lane it snaps to 0 or 1), curve (the interpolation type of the segment arriving AT this point from the previous point), shape (curve shaping factor -1 to 1; 0 is the neutral/linear shape), or resetShape (shape back to 0; mutually exclusive with shape). At least one edit is required. cursor+normalized together apply as a single undoable move; every other aspect is its own undo step (one Cmd-Z each, in the order value/move, curve, shape). A move is silently clamped between the neighboring points' cursors and the clip bounds — a point can never cross its neighbors (remove and re-add it to jump past one). The payload echoes the point read back from the engine (resulting index, cursor, normalized, curve, shape — the same field names get_clip_lane emits), never the request, so any clamp or snap is visible by comparison. Event indices are positional and shift on every insert or remove — re-read the lane rather than reuse an index from an earlier response, or pass atCursor to fail safely if it moved.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `lanePath` | string | yes | — | Canonical path of the parameter automation lane, e.g. /lx/timeline/composition/lane/4 (see list_clip_lanes; lane paths are positional — re-list rather than reuse one from an earlier response) |
| `index` | integer | yes | 0–2147483647 | Absolute 0-based index of the point in the lane's event list |
| `atCursor` | object | no | — | Optional guard: expected cursor of the event at index — rejects if the lane changed since it was read. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `cursor` | object | no | — | New time position for the point (clamped between the neighboring points and the clip bounds). Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `normalized` | number | no | — | New value as a normalized fraction of the parameter's range, 0-1 (not the raw parameter value). With cursor, both apply as one undoable move. |
| `curve` | string | no | one of: `POWER_EASE`, `POWER_S_CURVE`, `SMOOTHSTEP`, `SINUSOIDAL` | Interpolation curve of the segment arriving at this point from the previous point |
| `shape` | number | no | — | Curve shaping factor, -1 to 1 (0 = neutral: linear for POWER_EASE; negative and positive bend the segment toward its start/end) |
| `resetShape` | boolean | no | — | Reset the shaping factor to 0 (mutually exclusive with shape) |

### `remove_automation_point`

_mutating_

Remove one event from a clip lane by {lanePath, index}: an automation point on a parameter lane, or any other lane type's event — except MIDI note lanes, whose paired note-on/off events have no single-event removal. Returns the removed event's former index and cursor plus the lane's remaining eventCount. Event indices are positional and shift on every insert or remove — re-read the lane rather than reuse an index from an earlier response, or pass atCursor to fail safely if it moved. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `lanePath` | string | yes | — | Canonical lane path (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes |
| `index` | integer | yes | 0–2147483647 | Absolute 0-based position of the event in the lane's event list |
| `atCursor` | object | no | — | Optional guard: expected cursor of the event at index — rejects if the lane changed since it was read. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |

### `remove_clip_range`

_mutating_

Delete every event in the cursor range [from, to] (inclusive at both ends) on one clip lane. Lane-scoped by design — LX has no clip-wide range command; loop over the lanes from list_clip_lanes for a whole-clip cut. On MIDI note lanes, note-on/off pairs overlapping the range are removed together. Leaves the gap open: events after the range keep their cursors, and markers and clip length are unchanged (compose with set_clip_marker's truncate to also shorten the clip). A range containing no events succeeds with removedCount 0 and pushes nothing onto the undo stack; otherwise undoable in Chromatik with Cmd-Z. Event indices across the lane shift after a removal — re-read the lane rather than reuse indices from an earlier response.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `lanePath` | string | yes | — | Canonical lane path (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes |
| `from` | object | yes | — | Start of the range to delete. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `to` | object | yes | — | End of the range to delete (must not be before from). Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |

### `collapse_clip_range`

_mutating_

Collapse the automation envelope inside [from, to] on one clip lane: removes the interior events, keeping the first and last events in the range as the surviving boundary points (Chromatik's Collapse Envelope). Use it to flatten a busy recorded envelope into a single segment between its endpoints. A range holding fewer than three events has no interior — succeeds with removedCount 0 and pushes nothing onto the undo stack; otherwise undoable in Chromatik with Cmd-Z. Event indices across the lane shift after a collapse — re-read the lane rather than reuse indices from an earlier response.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `lanePath` | string | yes | — | Canonical lane path (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes |
| `from` | object | yes | — | Start of the range to collapse. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `to` | object | yes | — | End of the range to collapse (must not be before from). Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |

### `add_clip_note`

_mutating_

Insert a text-note event on a textNote lane (lanePath from list_clip_lanes, form <clipPath>/lane/<n>) at a cursor position, with optional length (default zero). Notes are annotations only — they never affect playback. Returns the resulting event {index, cursor, note, length, end} read back from the engine. Event indices are positional and shift on every insert or remove — re-read the lane rather than reuse an index from an earlier response; the event-editing tools (set_automation_point, remove_automation_point, set_clip_note) take an atCursor guard to fail safely if it moved. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `lanePath` | string | yes | — | Canonical path of the textNote lane (<clipPath>/lane/<n>, from list_clip_lanes); create one with add_notes_lane |
| `note` | string | yes | — | The note text |
| `cursor` | object | yes | — | Timeline position of the note. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `length` | object | no | — | Optional duration of the note event (a cursor-shaped span from its position; default zero). Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |

### `set_clip_note`

_mutating_

Edit the text-note event at {lanePath, index}: set its text (note), move it (cursor — clamped between the neighboring events and the clip length), and/or set its duration (length — floored at the minimum event length). At least one of note/cursor/length is required. The response echoes the event state read back from the engine, which may differ from the request due to clamping. Event indices are positional and shift on every insert or remove — re-read the lane rather than reuse an index from an earlier response, or pass atCursor to fail safely if it moved. Not undoable with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `lanePath` | string | yes | — | Canonical path of the textNote lane (<clipPath>/lane/<n>, from list_clip_lanes) |
| `index` | integer | yes | 0–2147483647 | Absolute 0-based position of the event in the lane's event list |
| `atCursor` | object | no | — | Optional guard: expected cursor of the event at index — rejects if the lane changed since it was read. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `note` | string | no | — | New note text (omit to keep) |
| `cursor` | object | no | — | New timeline position (omit to keep); clamped between the neighboring events and the clip length. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |
| `length` | object | no | — | New duration (omit to keep); floored at the minimum event length. Exactly one form: {millis} \| {beatCount[, beatBasis]} \| {bars[, beats, sixteenths]} \| {at[, offsetBeats \| offsetMillis]}. |

## Batch

Run several mutation-tool calls in one round-trip, all inside a single engine frame.
Undo is **not** batched: each operation still produces its own undo entry, and per the
tool's own description, one failing operation in a batch can wipe undo history for
earlier operations in that same batch even though they still report success.

### `apply_operations`

_mutating_

Apply up to 50 mutation-tool calls in one MCP round-trip. Every handler already runs on the LX engine thread, so a batch schedules onto it once and every operation lands in the same engine frame — no intermediate half-built state is ever rendered or output between operations, unlike issuing the same calls one at a time. Each entry is {tool, args}: 'tool' names any registered batchable mutation tool (by its normal tool name) and 'args' is exactly the argument object a top-level call to that tool would take. Every operations[i].tool is validated up front — an unknown name, a read-only tool, undo/redo, or apply_operations itself (batches cannot nest) fails the whole call with invalid_argument and applies nothing. Once validated, execution is continue-on-error: an operation that fails does not stop the ones after it. The response's results array has one entry per operation, in order: {index, ok: true, result} on success or {index, ok: false, code, message} on failure, using the same error codes a top-level call would return. Two sharp edges this tool does NOT smooth over: (1) it does not collapse the batch into one undo step — each operation still produces its own undo entry (or entries) exactly as if called individually, so undoing an N-operation batch takes N presses of Cmd-Z; (2) LX's lx.command.perform() wipes the entire undo/redo history when a command fails — in a batch, one failing operation can silently erase undo history for every earlier operation in the SAME batch, even though those operations still report ok: true; (3) all operations run inside one engine frame, so an I/O-heavy operation stalls the whole batch's cost onto that single frame — save_project (full project serialization plus a disk write, plus a .lxm write-through when syncModelFile is on) is now the worst case, ahead of reload_fixtures (which re-reads every .lxf from disk); a large or slow batch can hit the 30s executor timeout. A batch still queued at that point is cancelled; one already started on the engine thread cannot be interrupted safely and may still complete.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `operations` | array<object> | yes | — | Operations to apply, in order |

## OSC

Parameter payloads carry the address an OSC controller must send to. For most
parameters it equals the canonical path, but **modulator knobs answer at label-based
addresses** (`/lx/modulation/Knobs/macro1`, not `.../modulator/1/macro1`) — renaming a
modulator moves its OSC address. Ports are in `get_project_info` (defaults: 3030
receive / 4040 transmit).
