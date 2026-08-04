---
title: Tool reference
description: The full MCP tool surface — discovery, parameters, project persistence, structure, modulation, palette, snapshots, views, rendering, and batching.
---

Everything is addressed by canonical LX path (e.g. `/lx/mixer/channel/1/fader`), as
returned by the discovery tools. Mutations are undoable in Chromatik with Cmd-Z unless
noted.

## Discover

Full per-tool parameter tables (source-generated from the actual schemas — see
[Keeping this page honest](#keeping-this-page-honest)):

<!-- generated:start:discover -->

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

List one modulation engine's live modulators and wirings. Defaults to 'detail: summary' — the wiring graph only (modulators: path/label/class; modulations and triggers: path/sourcePath/targetPath) — the right choice for surveying a project; a real project can carry dozens of modulators and hundreds of wirings, and the full shape blows past client response limits. Pass 'detail: full' for today's complete shape (modulator OSC addresses/running state, and per-modulation range/polarity/rangePath to adjust depth via set_parameter). Wirings are paged in continuous-then-trigger order (100 by default, 250 maximum); pass nextCursor back as cursor until it is omitted. Modulators repeat on every page. Defaults to the global engine; pass scope (a device path) for a pattern/effect's own chain. Knob paths derive from a modulator's path (e.g. <path>/macro1).

| param | type | required | constraints | description |
|---|---|---|---|---|
| `scope` | string | no | — | Optional canonical path of a device (its own engine) or a modulation engine; omit for the global engine |
| `detail` | string | no | one of: `summary`, `full` | 'summary' (default) for the wiring graph only, or 'full' for today's complete payload (OSC addresses, running state, range/polarity/rangePath) |
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

See what the model is rendering by reading the composited output buffer. Pass include_image=true to get an actual PNG image of the current frame — use this whenever you need to visually inspect the render (e.g. confirming a pattern/effect change looks right, debugging the mapping, or answering 'what does this look like'). The API always returns a cheap numeric summary (non-black fraction, lit fraction, mean brightness, dominant colors, and an NxN mean-color grid) — the PNG is additional when requested. nonBlackFraction counts any pixel with a nonzero channel, so near-black residuals (e.g. a #101010 blur tail) inflate it even though they read as dark. litFraction excludes those residuals: it counts only pixels whose max channel exceeds litThreshold (default 26, ~10% of full scale — a documented heuristic, not perceptual luminance; raise it to make litFraction stricter) and is the field to use when judging negative space or whether an area actually reads as dark. litThreshold=0 makes litFraction equal to nonBlackFraction (max > 0 is the nonBlack condition); litThreshold=255 makes litFraction always 0.0, since no channel can exceed the maximum. Image content is token-expensive, so default to the numeric summary and only request the PNG when actually looking at the picture matters. Supports orthographic front/top/side views and main/cue/aux output buses.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `view` | string | no | one of: `front`, `top`, `side` | Orthographic view plane (default front: x/y as seen from the front; top: x/z; side: z/y) |
| `width` | integer | no | 64–1024 | Image width in pixels (default 256); height follows the model's aspect ratio |
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

<!-- generated:end -->

## Save the project & model

Everything built over this API lives only in the running engine until `save_project`
writes a `.lxp` file — restart Chromatik without saving and it's gone. Neither tool is
`LXCommand`-backed (writing a file is an action, not undoable engine state), so neither
appears in Chromatik's undo history. `save_project` writes through to a linked external
model file whenever `get_project_info`'s `model.syncModelFile` is on — `save_model` to a
new path first if that file is shared with other projects on the rig and shouldn't be
touched.

<!-- generated:start:project -->

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

<!-- generated:end -->

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

<!-- generated:start:parameters -->

### `set_parameter`

_mutating_

Set a parameter by its canonical LX path (e.g. /lx/mixer/channel/1/fader). The value's type must match the parameter: a number for numeric/bounded, a boolean for toggles, a string for text. Discrete/selector parameters accept either the in-range integer value or an option name string (e.g. a device's 'view' selector accepts the target view's label) — an unknown or ambiguous option name is rejected with the valid options listed. Aggregate parameters (color, MIDI filter) are set via their component paths (e.g. .../hue, .../saturation, .../brightness); momentary triggers fire via fire_trigger. Undoable in Chromatik with Cmd-Z. On a parameter with live modulations the response's value is the effective (modulated) reading — baseValue echoes the value you set.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical LX path of the parameter, as returned by the list/get tools |
| `value` | number \| boolean \| string | yes | — | New value; its type must match the parameter (number, boolean, or string) |

<!-- generated:end -->

## Build structure: channels, patterns, effect chains

Effect chains run serially in list order, on channels, the master bus, or an individual
pattern. Structural paths are 1-based and reindex on remove/insert — re-list rather than
reusing cached paths.

<!-- generated:start:structure -->

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

### `add_pattern`

_mutating_

Add a pattern ('class', from list_available_patterns — either the full class name or the short name it lists) to a channel ('containerPath'). Pass an optional 0-based index to insert at a specific position; omit to append. The first pattern added to an empty channel auto-activates. Targets channels only (not PatternRacks). Inserting shifts the 1-based paths of later sibling patterns — re-list rather than reusing cached paths. Undoable in Chromatik with Cmd-Z.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `containerPath` | string | yes | — | Canonical path of the channel, e.g. /lx/mixer/channel/1 |
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

<!-- generated:end -->

## Map macro knobs (and any modulation)

<!-- generated:start:modulation -->

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

<!-- generated:end -->

## Palette & snapshots

<!-- generated:start:palette -->

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

<!-- generated:end -->

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

<!-- generated:start:views -->

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

<!-- generated:end -->

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

<!-- generated:start:fixtures -->

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

Reposition a fixture within lx.structure.fixtures (0-based 'index', clamped into [0, fixtureCount - 1]). Undoable with Cmd-Z. Every fixture's path is POSITIONAL (/lx/structure/fixture/N, 1-indexed) and shifts for this fixture and any it moved past — re-list (list_fixtures) rather than reuse a held path. Rejected when the structure is in static-model mode.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `path` | string | yes | — | Canonical path of the fixture to move, e.g. /lx/structure/fixture/2 |
| `index` | integer | yes | — | 0-based target position in lx.structure.fixtures, clamped into [0, fixtureCount - 1] |

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

<!-- generated:end -->

## MIDI

Read-side only for now: devices (inputs/outputs), the parameter mappings incoming
MIDI drives, and connected control surfaces. Ports/mappings/surfaces carry no
canonical path, so each is addressed by its 0-based list index — re-list before
reusing one, since indices shift when the underlying list changes.

<!-- generated:start:midi -->

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

Set one or more of a MIDI input's routing flags by its 0-based index into list_midi_devices' inputs list: channelEnabled (forward notes/CCs to channel and modulator devices), controlEnabled (feed the control-mapping layer — see list_midi_mappings), syncEnabled (this port's MIDI clock drives the engine tempo). At least one flag must be provided; flags left unset are unchanged. enabled is a derived union of the three and cannot be set directly. Returns the updated input in list_midi_devices' shape. Not undoable — LX has no undo command for these flags.

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

<!-- generated:end -->

## Batch

Run several mutation-tool calls in one round-trip, all inside a single engine frame.
Undo is **not** batched: each operation still produces its own undo entry, and per the
tool's own description, one failing operation in a batch can wipe undo history for
earlier operations in that same batch even though they still report success.

<!-- generated:start:batch -->

### `apply_operations`

_mutating_

Apply up to 50 mutation-tool calls in one MCP round-trip. Every handler already runs on the LX engine thread, so a batch schedules onto it once and every operation lands in the same engine frame — no intermediate half-built state is ever rendered or output between operations, unlike issuing the same calls one at a time. Each entry is {tool, args}: 'tool' names any registered mutation tool (by its normal tool name) and 'args' is exactly the argument object a top-level call to that tool would take. Every operations[i].tool is validated up front — an unknown name, a read-only tool, or apply_operations itself (batches cannot nest) fails the whole call with invalid_argument and applies nothing. Once validated, execution is continue-on-error: an operation that fails does not stop the ones after it. The response's results array has one entry per operation, in order: {index, ok: true, result} on success or {index, ok: false, code, message} on failure, using the same error codes a top-level call would return. Two sharp edges this tool does NOT smooth over: (1) it does not collapse the batch into one undo step — each operation still produces its own undo entry (or entries) exactly as if called individually, so undoing an N-operation batch takes N presses of Cmd-Z; (2) LX's lx.command.perform() wipes the entire undo/redo history when a command fails — in a batch, one failing operation can silently erase undo history for every earlier operation in the SAME batch, even though those operations still report ok: true; (3) all operations run inside one engine frame, so an I/O-heavy operation stalls the whole batch's cost onto that single frame — save_project (full project serialization plus a disk write, plus a .lxm write-through when syncModelFile is on) is now the worst case, ahead of reload_fixtures (which re-reads every .lxf from disk); a large or slow batch can hit the 30s executor timeout. A batch still queued at that point is cancelled; one already started on the engine thread cannot be interrupted safely and may still complete.

| param | type | required | constraints | description |
|---|---|---|---|---|
| `operations` | array<object> | yes | — | Operations to apply, in order |

<!-- generated:end -->

## OSC

Parameter payloads carry the address an OSC controller must send to. For most
parameters it equals the canonical path, but **modulator knobs answer at label-based
addresses** (`/lx/modulation/Knobs/macro1`, not `.../modulator/1/macro1`) — renaming a
modulator moves its OSC address. Ports are in `get_project_info` (defaults: 3030
receive / 4040 transmit).

## Keeping this page honest

The tables above are generated from the same JSON-Schema `inputSchema` every tool
advertises over MCP (`site/src/data/tools.json`, produced by
`package/scripts/dump-tool-catalog.sh`) — never hand-transcribed, so they can't drift from
what a client actually sees.
