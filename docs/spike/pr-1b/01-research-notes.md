# PR-1b Research Notes: LXCommand Surface Enumeration

## Overview
This document catalogs every command category and action class in `LXCommand.java`, enumerated with full constructor signatures and operation descriptions. It also inventories supporting types, planned-tool reconnaissance findings, and introspection surface for phase-2 capability.

## LXCommand Categories and Actions

### Category: Parameter
Actions for parameter-level mutations. These commands are foundational for setting values on `LXParameter`, `DiscreteParameter`, `LXNormalizedParameter`, `StringParameter`, and `ColorParameter`.

#### `LXCommand.Parameter.Reset`
- **Constructor**: `public Reset(LXParameter parameter)`
- **Mutates**: Resets parameter to its default/base value. Stores original value and restores on undo.
- **Cite**: LXCommand.java:434-464

#### `LXCommand.Parameter.SetValue`
- **Constructors**: 
  - `public SetValue(DiscreteParameter parameter, int value)`
  - `public SetValue(LXParameter parameter, double value)`
- **Mutates**: Sets a parameter to a new double or discrete int value. Polymorphic to handle both discrete and generic parameters.
- **Cite**: LXCommand.java:466-553

#### `LXCommand.Parameter.SetIndex`
- **Constructor**: `public SetIndex(DiscreteParameter parameter, int index)`
- **Mutates**: Sets a DiscreteParameter via index (adds parameter's min value to the provided index).
- **Cite**: LXCommand.java:590-595

#### `LXCommand.Parameter.SetColor`
- **Constructors**:
  - `public SetColor(ColorParameter colorParameter)`
  - `public SetColor(ColorParameter colorParameter, double hue, double saturation)`
- **Mutates**: Sets hue and saturation on a `ColorParameter`.
- **Cite**: LXCommand.java:597-641

#### `LXCommand.Parameter.Increment`
- **Constructors**:
  - `public Increment(DiscreteParameter parameter)`
  - `public Increment(DiscreteParameter parameter, boolean alwaysWrap)`
  - `public Increment(DiscreteParameter parameter, int amount)`
  - `public Increment(DiscreteParameter parameter, int amount, boolean alwaysWrap)`
- **Mutates**: Increments a discrete parameter by a specified amount, optionally wrapping.
- **Cite**: LXCommand.java:643-688

#### `LXCommand.Parameter.Decrement`
- **Constructors**:
  - `public Decrement(DiscreteParameter parameter)`
  - `public Decrement(DiscreteParameter parameter, int amount)`
  - `public Decrement(DiscreteParameter parameter, boolean alwaysWrap)`
  - `public Decrement(DiscreteParameter parameter, int amount, boolean alwaysWrap)`
- **Mutates**: Decrements a discrete parameter by a specified amount, optionally wrapping.
- **Cite**: LXCommand.java:690-735

#### `LXCommand.Parameter.Toggle`
- **Constructor**: `public Toggle(BooleanParameter parameter)`
- **Mutates**: Toggles a boolean parameter on/off.
- **Cite**: LXCommand.java:737-762

#### `LXCommand.Parameter.SetNormalized`
- **Constructors**:
  - `public SetNormalized(LXNormalizedParameter parameter)`
  - `public SetNormalized(BooleanParameter parameter, boolean value)`
  - `public SetNormalized(LXNormalizedParameter parameter, double newValue)`
- **Mutates**: Sets a normalized parameter to a 0-1 value.
- **Cite**: LXCommand.java:764-807

#### `LXCommand.Parameter.SetString`
- **Constructor**: `public SetString(StringParameter parameter, String value)`
- **Mutates**: Sets a string parameter to a new string value.
- **Cite**: LXCommand.java:809-835

#### `LXCommand.Parameter.MultiSetValue`
- **Constructor**: `protected MultiSetValue(String description)`
- **Mutates**: Composite command that batches multiple `Parameter.SetValue` operations. Base class for operations like `AutoMute`.
- **Cite**: LXCommand.java:555-588

---

### Category: Channel
Actions for pattern management within channels/pattern engines and channel audio setup.

#### `LXCommand.Channel.SetFader`
- **Constructor**: `public SetFader(LXAbstractChannel channel, boolean enabled, double fader)`
- **Mutates**: Sets both `channel.enabled` and `channel.fader` in a single undoable action. Combines two `Parameter.Set*` operations.
- **Cite**: LXCommand.java:840-866

#### `LXCommand.Channel.AddPattern`
- **Constructors**:
  - `public AddPattern(LXPatternEngine.Container container, Class<? extends LXPattern> patternClass)`
  - `public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass)`
  - `public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass, int patternIndex)`
  - `public AddPattern(LXPatternEngine.Container container, Class<? extends LXPattern> patternClass, JsonObject patternObject)`
  - `public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass, JsonObject patternObject)`
  - `public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass, JsonObject patternObject, int patternIndex)`
- **Mutates**: Instantiates and adds a pattern to a pattern engine. Stores serialized pattern for redo. Mutates engine's pattern list.
- **Cite**: LXCommand.java:868-944

#### `LXCommand.Channel.RemovePattern`
- **Constructors**:
  - `public RemovePattern(LXPatternEngine.Container container, LXPattern pattern)`
  - `public RemovePattern(LXPatternEngine engine, LXPattern pattern)`
  - `private RemovePattern(LXPatternEngine engine, LXPattern pattern, ModulationContext context)` (internal)
- **Mutates**: Removes a pattern from the engine. Also removes associated modulations, MIDI mappings, snapshot views, clip lanes, and remote control references.
- **Cite**: LXCommand.java:946-1012

#### `LXCommand.Channel.RemovePatterns`
- **Constructor**: `public RemovePatterns(LXPatternEngine patternEngine, List<LXPattern> patterns)`
- **Mutates**: Batch removes multiple patterns with shared modulation context to avoid duplicate removals.
- **Cite**: LXCommand.java:1014-1047

#### `LXCommand.Channel.GroupPatterns`
- **Constructor**: `public GroupPatterns(LXPatternEngine patternEngine, List<LXPattern> patterns)`
- **Mutates**: Groups multiple patterns into a `PatternRack`. Removes originals, adds rack, copies pattern engine parameters, updates path references for modulations/MIDI mappings.
- **Cite**: LXCommand.java:1049-1190

#### `LXCommand.Channel.ReloadPattern`
- **Constructors**:
  - `public ReloadPattern(LXPatternEngine.Container container, LXPattern pattern)`
  - `public ReloadPattern(LXPatternEngine engine, LXPattern pattern)`
- **Mutates**: Removes and immediately re-adds a pattern to reload its class code, preserving all modulations and automation (marked as ignored).
- **Cite**: LXCommand.java:1192-1226

#### `LXCommand.Channel.MovePattern`
- **Constructors**:
  - `public MovePattern(LXPatternEngine.Container container, LXPattern pattern, int toIndex)`
  - `public MovePattern(LXPatternEngine engine, LXPattern pattern, int toIndex)`
- **Mutates**: Moves a pattern to a new index in the pattern list.
- **Cite**: LXCommand.java:1228-1264

#### `LXCommand.Channel.GoPattern`
- **Constructors**:
  - `public GoPattern(LXPatternEngine.Container container, LXPattern nextPattern)`
  - `public GoPattern(LXPatternEngine engine, LXPattern nextPattern)`
- **Mutates**: Transitions to a different active pattern.
- **Cite**: LXCommand.java:1266-1300

#### `LXCommand.Channel.PatternCycle`
- **Constructor**: `public PatternCycle(LXPatternEngine patternEngine)`
- **Mutates**: Cycles to the next pattern if in playlist mode, captured as ignored if not applicable.
- **Cite**: LXCommand.java:1302-1349

#### `LXCommand.Channel.AddEffect`
- **Constructors**:
  - `public AddEffect(LXComponent parent, Class<? extends LXEffect> effectClass)`
  - `public AddEffect(LXComponent parent, Class<? extends LXEffect> effectClass, JsonObject effectObj)`
- **Mutates**: Adds an effect to a component that implements `LXEffect.Container` (channels, patterns, etc.).
- **Cite**: LXCommand.java:1358-1403

#### `LXCommand.Channel.RemoveEffect`
- **Constructor**: `public RemoveEffect(LXComponent container, LXEffect effect)`
- **Mutates**: Removes an effect from its container. Propagates removal to modulations, MIDI mappings, snapshot views, and clip lanes.
- **Cite**: LXCommand.java:1405-1451

#### `LXCommand.Channel.ReloadEffect`
- **Constructor**: `public ReloadEffect(LXComponent container, LXEffect effect)`
- **Mutates**: Like `ReloadPattern`, reloads effect class code without removing (marked ignored).
- **Cite**: LXCommand.java:1453-1488

#### `LXCommand.Channel.MoveEffect`
- **Constructor**: `public MoveEffect(LXComponent parent, LXEffect effect, int toIndex)`
- **Mutates**: Moves an effect to a new position in container's effect list.
- **Cite**: LXCommand.java:1490-1528

#### `LXCommand.Channel.RelocateEffect`
- **Constructor**: `public RelocateEffect(LXEffect effect, LXEffect.Container target, int effectIndex)`
- **Mutates**: Removes effect from one container and adds it to another. Updates path references in modulations and MIDI mappings.
- **Cite**: LXCommand.java:1530-1614

---

### Category: Device
Actions for device-level operations: preset loading and remote control configuration.

#### `LXCommand.Device.LoadPreset`
- **Constructor**: `public LoadPreset(LXComponent device, File file)`
- **Mutates**: Loads a preset file into a `LXPresetComponent`. Stores prior state for undo.
- **Cite**: LXCommand.java:1620-1649

#### `LXCommand.Device.SetRemoteControls`
- **Constructor**: `public SetRemoteControls(LXDeviceComponent device, LXListenableNormalizedParameter[] remoteControls)`
- **Mutates**: Sets the array of custom remote control parameters for a device. Converts parameters to canonical paths for serialization.
- **Cite**: LXCommand.java:1682-1727

#### `LXCommand.Device.ClearRemoteControls`
- **Constructor**: `public ClearRemoteControls(LXDeviceComponent device)`
- **Mutates**: Clears all custom remote controls from a device.
- **Cite**: LXCommand.java:1729-1749

---

### Category: Mixer
Actions for channel and group management in the mixer.

#### `LXCommand.Mixer.AddChannel`
- **Constructors**:
  - `public AddChannel()`
  - `public AddChannel(JsonObject channelObj)`
  - `public AddChannel(JsonObject channelObj, int index)`
  - `public AddChannel(Class<? extends LXPattern> patternClass)`
  - `public AddChannel(JsonObject channelObj, Class<? extends LXPattern> patternClass)`
  - `public AddChannel(JsonObject channelObj, Class<? extends LXPattern> patternClass, int index)`
- **Mutates**: Adds a new `LXChannel` to the mixer. Can create from saved JSON, instantiate with a default pattern class, or create empty. Sets focus and selection.
- **Cite**: LXCommand.java:1754-1819

#### `LXCommand.Mixer.MoveChannel`
- **Constructor**: `public MoveChannel(LXAbstractChannel channel, int delta)`
- **Mutates**: Moves a channel by a relative delta in the mixer's channel list.
- **Cite**: LXCommand.java:1821-1846

#### `LXCommand.Mixer.DropChannel`
- **Constructor**: `public DropChannel(LXAbstractChannel channel, int index, LXGroup group)`
- **Mutates**: Moves a channel to a specific index, optionally into a group. Handles index adjustment for leftward group moves.
- **Cite**: LXCommand.java:1848-1887

#### `LXCommand.Mixer.RemoveChannel`
- **Constructor**: `public RemoveChannel(LXAbstractChannel channel)`
- **Mutates**: Removes a channel from the mixer. Recursively removes group children, restores focus and selection, and reattaches modulations/MIDI mappings.
- **Cite**: LXCommand.java:1890-1950

#### `LXCommand.Mixer.RemoveSelectedChannels`
- **Constructor**: `public RemoveSelectedChannels(LX lx)`
- **Mutates**: Batch removes all selected channels (including groups, which removes their children).
- **Cite**: LXCommand.java:1952-1994

#### `LXCommand.Mixer.Ungroup`
- **Constructor**: `public Ungroup(LXGroup group)`
- **Mutates**: Dissolves a group, promoting all child channels to top level.
- **Cite**: LXCommand.java:1996-2031

#### `LXCommand.Mixer.UngroupChannel`
- **Constructor**: `public UngroupChannel(LXChannel channel)`
- **Mutates**: Removes a single channel from its group to top level.
- **Cite**: LXCommand.java:2033-2061

#### `LXCommand.Mixer.GroupSelectedChannels`
- **Constructor**: `public GroupSelectedChannels(LX lx)`
- **Mutates**: Creates a new group and moves all selected channels into it.
- **Cite**: LXCommand.java:2063-2105

#### `LXCommand.Mixer.AutoMute`
- **Constructors**:
  - `public AutoMute(LXPatternEngine patternEngine, boolean autoMute)`
  - `public AutoMute(LXMixerEngine mixer, boolean autoMute)`
- **Mutates**: Batch sets `autoMute` on all patterns in an engine or all channels in the mixer.
- **Cite**: LXCommand.java:2107-2122

---

### Category: Modulation
Actions for modulation graph management: adding/removing modulators and connections.

#### `LXCommand.Modulation.AddModulator`
- **Constructors**:
  - `public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass)`
  - `public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass, int modulationColor)`
  - `public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass, JsonObject modulatorObj)`
  - `public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass, JsonObject modulatorObj, int modulationColor)`
- **Mutates**: Instantiates and adds a modulator to the modulation engine. Optionally sets color and restores from JSON.
- **Cite**: LXCommand.java:2128-2188

#### `LXCommand.Modulation.MoveModulator`
- **Constructor**: `public MoveModulator(LXModulationEngine modulation, LXModulator modulator, int index)`
- **Mutates**: Moves a modulator to a new index in the modulation engine's modulator list.
- **Cite**: LXCommand.java:2190-2221

#### `LXCommand.Modulation.RemoveModulator`
- **Constructor**: `public RemoveModulator(LXModulationEngine modulation, LXModulator modulator)`
- **Mutates**: Removes a modulator and restores all associated modulations on undo.
- **Cite**: LXCommand.java:2223-2262

#### `LXCommand.Modulation.AddModulation`
- **Constructor**: `public AddModulation(LXModulationEngine engine, LXNormalizedParameter source, LXCompoundModulation.Target target)`
- **Mutates**: Creates a modulation connection from a source parameter/component to a target parameter. Source can be a component itself (if it implements `LXNormalizedParameter`).
- **Cite**: LXCommand.java:2264-2338

#### `LXCommand.Modulation.RemoveModulation`
- **Constructor**: `public RemoveModulation(LXModulationEngine engine, LXCompoundModulation modulation)`
- **Mutates**: Removes a modulation and can move it to a new path reference if a component is relocated.
- **Cite**: LXCommand.java:2341-2395

#### `LXCommand.Modulation.RemoveModulations`
- **Constructor**: `public RemoveModulations(LXCompoundModulation.Target parameter)`
- **Mutates**: Batch removes all modulations targeting a specific parameter.
- **Cite**: LXCommand.java:2397-2425

#### `LXCommand.Modulation.AddTrigger`
- **Constructor**: `public AddTrigger(LXModulationEngine engine, BooleanParameter source, BooleanParameter target)`
- **Mutates**: Adds a trigger (boolean modulation) from a source to target boolean parameter.
- **Cite**: LXCommand.java:2427-2462

#### `LXCommand.Modulation.RemoveTrigger`
- **Constructor**: `public RemoveTrigger(LXModulationEngine engine, LXTriggerModulation trigger)`
- **Mutates**: Removes a trigger and can move it to a new path reference.
- **Cite**: LXCommand.java:2464-2517

#### `LXCommand.Modulation.Remove`
- **Constructor**: `public Remove(LXModulationEngine engine, List<LXParameterModulation> modulations)`
- **Mutates**: Polymorphic batch removal for both regular and trigger modulations.
- **Cite**: LXCommand.java:2519-2551

---

### Category: Palette
Actions for color palette management: colors, swatches, imports.

#### `LXCommand.Palette.AddColor`
- **Constructor**: `public AddColor(LXSwatch swatch)`
- **Mutates**: Adds a new color to a swatch and stores its JSON for redo.
- **Cite**: LXCommand.java:2556-2585

#### `LXCommand.Palette.RemoveColor`
- **Constructor**: `public RemoveColor(LXDynamicColor color)`
- **Mutates**: Removes a color from its swatch.
- **Cite**: LXCommand.java:2587-2617

#### `LXCommand.Palette.SaveSwatch`
- **Constructors**:
  - `public SaveSwatch()`
  - `public SaveSwatch(JsonObject initialObj, int index)`
- **Mutates**: Creates and saves a new swatch from current palette state or restores from JSON.
- **Cite**: LXCommand.java:2619-2661

#### `LXCommand.Palette.RemoveSwatch`
- **Constructor**: `public RemoveSwatch(LXSwatch swatch)`
- **Mutates**: Removes a swatch from the palette.
- **Cite**: LXCommand.java:2663-2692

#### `LXCommand.Palette.MoveSwatch`
- **Constructor**: `public MoveSwatch(LXSwatch swatch, int toIndex)`
- **Mutates**: Moves a swatch to a new index in the palette's swatch list.
- **Cite**: LXCommand.java:2694-2722

#### `LXCommand.Palette.SetSwatch`
- **Constructor**: `public SetSwatch(LXSwatch swatch)`
- **Mutates**: Sets the active palette swatch to a saved swatch.
- **Cite**: LXCommand.java:2725-2756

#### `LXCommand.Palette.ImportSwatches`
- **Constructor**: `public ImportSwatches(LXPalette palette, File file)`
- **Mutates**: Imports swatches from a file. Stores references to maintain ID stability across redo.
- **Cite**: LXCommand.java:2758-2811

---

### Category: Snapshots
Actions for global snapshot management and snapshot-level updates.

#### `LXCommand.Snapshots.AddSnapshot`
- **Constructors**:
  - `public AddSnapshot()`
  - `public AddSnapshot(JsonObject snapshotObj, int index)`
- **Mutates**: Creates a new snapshot capturing global state, or restores from JSON.
- **Cite**: LXCommand.java:2816-2862

#### `LXCommand.Snapshots.MoveSnapshot`
- **Constructor**: `public MoveSnapshot(LXGlobalSnapshot snapshot, int toIndex)`
- **Mutates**: Moves a snapshot to a new index.
- **Cite**: LXCommand.java:2864-2890

#### `LXCommand.Snapshots.RemoveSnapshot`
- **Constructor**: `public RemoveSnapshot(LXGlobalSnapshot snapshot)`
- **Mutates**: Removes a snapshot from the snapshot engine.
- **Cite**: LXCommand.java:2892-2922

#### `LXCommand.Snapshots.Update`
- **Constructor**: `public Update(LXSnapshot snapshot)`
- **Mutates**: Updates a snapshot to capture current state (any snapshot type: global or clip-specific).
- **Cite**: LXCommand.java:2924-2948

#### `LXCommand.Snapshots.Recall`
- **Constructor**: `public Recall(LXGlobalSnapshot snapshot)`
- **Mutates**: Recalls a global snapshot, generating sub-commands for parameter/pattern changes.
- **Cite**: LXCommand.java:2951-2983

#### `LXCommand.Snapshots.RecallImmediate`
- **Constructor**: `public RecallImmediate(LXClipSnapshot snapshot)`
- **Mutates**: Recalls a clip snapshot immediately (non-global).
- **Cite**: LXCommand.java:2985-3010

#### `LXCommand.Snapshots.RemoveView`
- **Constructor**: `public RemoveView(LXSnapshot.View view)`
- **Mutates**: Removes a view (parameter tracking) from a snapshot. Can move views when parent component relocates.
- **Cite**: LXCommand.java:3012-3045

#### `LXCommand.Snapshots.RemoveViews`
- **Constructor**: `public RemoveViews(String label, List<LXSnapshot.View> views)`
- **Mutates**: Batch removes multiple snapshot views.
- **Cite**: LXCommand.java:3047-3071

#### `LXCommand.Snapshots.UpdateView`
- **Constructors** (polymorphic on parameter type):
  - `public UpdateView(LXSnapshot.ParameterView view, boolean toggle)`
  - `public UpdateView(LXSnapshot.ParameterView view, BoundedParameter replacement)`
  - `public UpdateView(LXSnapshot.ParameterView view, DiscreteParameter replacement)`
  - `public UpdateView(LXSnapshot.ParameterView view, StringParameter replacement)`
- **Mutates**: Updates a view's captured value to match a replacement parameter's current state.
- **Cite**: LXCommand.java:3073-3170 (partial)

---

### Category: Structure
Actions for fixture and view management in the data structure.

#### `LXCommand.Structure.AddFixture`
- **Constructors**:
  - `public AddFixture(Class<? extends LXFixture> fixtureClass)`
  - `public AddFixture(Class<? extends LXFixture> fixtureClass, int index)`
  - `public AddFixture(Class<? extends LXFixture> fixtureClass, JsonObject fixtureObj)`
  - `public AddFixture(Class<? extends LXFixture> fixtureClass, JsonObject fixtureObj, int index)`
  - `public AddFixture(String fixtureType)`
- **Mutates**: Instantiates and adds a fixture. Can instantiate from class, JSON fixture definition (by type string), or load from JSON.
- **Cite**: LXCommand.java:3266-3334

#### `LXCommand.Structure.RemoveFixture`
- **Constructor**: `public RemoveFixture(LXFixture fixture)`
- **Mutates**: Removes a fixture from the structure and reattaches modulations.
- **Cite**: LXCommand.java:3336-3370

#### `LXCommand.Structure.RemoveSelectedFixtures`
- **Constructor**: `public RemoveSelectedFixtures(LXStructure structure)`
- **Mutates**: Batch removes all selected fixtures.
- **Cite**: LXCommand.java:3372-3407

#### `LXCommand.Structure.MoveFixture`
- **Constructor**: `public MoveFixture(LXFixture fixture, int index)`
- **Mutates**: Moves a fixture to a new index in the fixture list.
- **Cite**: LXCommand.java:3409-3435

#### `LXCommand.Structure.NewModel`
- **Constructor**: `public NewModel(LXStructure structure)`
- **Mutates**: Clears all fixtures (creates new dynamic model).
- **Cite**: LXCommand.java:3437-3464

#### `LXCommand.Structure.ArrangeFixtures`
- **Constructor**: `public ArrangeFixtures()`
- **Mutates**: Subclass of `Parameter.MultiSetValue` for batch arrangement operations on fixture parameters.
- **Cite**: LXCommand.java:3466-3472

#### `LXCommand.Structure.ModifyFixturePositions`
- **Constructor**: `public ModifyFixturePositions()`
- **Mutates**: Interactive command that accumulates position parameter changes (X, Y, Z) during drag operations.
- **Cite**: LXCommand.java:3474-3524

#### `LXCommand.Structure.AddView`
- **Constructors**:
  - `public AddView()`
  - `public AddView(JsonObject initialObj, int index)`
- **Mutates**: Creates a new view definition or restores from JSON.
- **Cite**: LXCommand.java:3526-3568

#### `LXCommand.Structure.RemoveView`
- **Constructor**: `public RemoveView(LXViewDefinition view)`
- **Mutates**: Removes a view definition.
- **Cite**: LXCommand.java:3570-3599

#### `LXCommand.Structure.MoveView`
- **Constructor**: `public MoveView(LXViewDefinition view, int toIndex)`
- **Mutates**: Moves a view to a new index.
- **Cite**: LXCommand.java:3601-3628

#### `LXCommand.Structure.ImportViews`
- **Constructor**: `public ImportViews(File file)`
- **Mutates**: Imports view definitions from a file.
- **Cite**: LXCommand.java:3630-3683

---

### Category: Clip
Actions for clip and clip lane management, including event editing.

#### `LXCommand.Clip.Add`
- **Constructors**:
  - `public Add(LXBus bus, int index, boolean enableSnapshot)`
  - `public Add(LXBus bus, int index, JsonObject clipObj)`
- **Mutates**: Adds or replaces a clip at a given index. Stores old clip for undo.
- **Cite**: LXCommand.java:3688-3740

#### `LXCommand.Clip.Remove`
- **Constructor**: `public Remove(LXClip clip)`
- **Mutates**: Removes a clip from a bus.
- **Cite**: LXCommand.java:3742-3769

#### `LXCommand.Clip.Record`
- **Constructor**: `public Record(LXClip clip)`
- **Mutates**: Records clip state before and after, allowing undo of recordings.
- **Cite**: LXCommand.java:3771-3802

#### `LXCommand.Clip.SetMarker`
- **Constructor**: `public SetMarker(LXClip clip, Marker marker, Cursor toCursor)`
- **Mutates**: Moves a clip marker (LOOP_START, LOOP_END, PLAY_START, PLAY_END) to a new cursor position.
- **Cite**: LXCommand.java:3854-3891

#### `LXCommand.Clip.MoveMarker`
- **Constructors**:
  - `public MoveMarker(LXClip clip, Marker marker, Cursor increment)`
  - `public MoveMarker(LXClip clip, Marker marker, Cursor increment, Operation op)`
- **Mutates**: Moves a marker by a relative increment (ADD or SUBTRACT operation).
- **Cite**: LXCommand.java:3893-3917

#### `LXCommand.Clip.MoveLane`
- **Constructor**: `public MoveLane(LXClipLane<?> lane, int index)`
- **Mutates**: Moves a clip lane to a new index within a clip.
- **Cite**: LXCommand.java:3919-3946

#### `LXCommand.Clip.RemoveClipLane`
- **Constructor**: `public RemoveClipLane(LXClipLane<?> parameterLane)`
- **Mutates**: Removes a clip lane (parameter automation lane, pattern lane, or MIDI lane) from a clip.
- **Cite**: LXCommand.java:3948-3986

#### `LXCommand.Clip.Event.Remove<T>`
- **Constructor**: `public Remove(LXClipLane<T> clipLane, LXClipEvent<T> clipEvent)`
- **Mutates**: Removes a single event from a clip lane.
- **Cite**: LXCommand.java:3990-4022

#### `LXCommand.Clip.Event.RemoveRange`
- **Constructor**: `public RemoveRange(LXClipLane<?> clipLane, Cursor from, Cursor to)`
- **Mutates**: Removes all events within a cursor range from a clip lane.
- **Cite**: LXCommand.java:4024-4059

#### `LXCommand.Clip.Event.SetCursors<T>`
- **Constructors**:
  - `public SetCursors(LXClipLane<T> clipLane, Cursor fromSelectionMin, Cursor fromSelectionMax, Map<T, Double> fromValues, Map<T, Cursor> fromCursors, Map<T, Cursor> toCursors)`
  - `public SetCursors(... , Runnable undoHook)` (with hook)
- **Mutates**: Modifies event timing/stretching/clearing within a clip lane via interactive operations (drag-handles, move operations). Supports STRETCH_TO_LEFT, SHORTEN_FROM_LEFT, CLEAR_FROM_LEFT, REVERSE operations.
- **Cite**: LXCommand.java:4061-4172

#### `LXCommand.Clip.Event.Midi.RemoveNote`
- **Constructor**: `public RemoveNote(MidiNoteClipLane clipLane, MidiNoteClipEvent midiNote)`
- **Mutates**: Removes a note (pair of NOTE ON/OFF events) from a MIDI clip lane.
- **Cite**: LXCommand.java:4174-4211

#### `LXCommand.Clip.Event.Midi.SetVelocity`
- **Constructor**: `public SetVelocity(MidiNoteClipLane clipLane, MidiNoteClipEvent midiNote)`
- **Mutates**: Changes the velocity of a MIDI note.
- **Cite**: LXCommand.java:4213-4258

#### `LXCommand.Clip.Event.Midi.SetChannel`
- **Constructor**: `public SetChannel(MidiNoteClipLane clipLane, MidiNoteClipEvent midiNote)`
- **Mutates**: Changes the MIDI channel of a note (updates both NOTE ON and OFF).
- **Cite**: LXCommand.java:4260-4310

#### `LXCommand.Clip.Event.Midi.EditNote`
- **Constructors**:
  - `public EditNote(MidiNoteClipLane clipLane, int pitch, int velocity, Cursor start, Cursor end)`
  - `public EditNote(MidiNoteClipLane clipLane, MidiNoteClipEvent noteOn)`
- **Mutates**: Edits all properties of a MIDI note (pitch, velocity, start, end timing). Accumulates original state on first perform.
- **Cite**: LXCommand.java:4312-4417

#### `LXCommand.Clip.Event.Midi.InsertNote`
- **Constructor**: `public InsertNote(MidiNoteClipLane clipLane, int pitch, int velocity, Cursor start, Cursor end)`
- **Mutates**: Inserts a new MIDI note into a clip lane.
- **Cite**: LXCommand.java:4419-4470+ (continued)

#### `LXCommand.Clip.Event.Pattern.*`
- Commands for pattern clip events (referenced in file at 4470+); exact structure follows MIDI pattern but for pattern references.

---

### Category: Osc
Actions for OSC connection management.

#### `LXCommand.Osc.AddInput`
- **Constructor**: `public AddInput()`
- **Mutates**: Adds a new OSC input connection to the engine.
- **Cite**: LXCommand.java:4806-4827

#### `LXCommand.Osc.RemoveInput`
- **Constructor**: `public RemoveInput(LXOscConnection.Input input)`
- **Mutates**: Removes an OSC input connection and stores its JSON for undo.
- **Cite**: LXCommand.java:4829-4855

#### `LXCommand.Osc.AddOutput`
- **Constructor**: `public AddOutput()`
- **Mutates**: Adds a new OSC output connection.
- **Cite**: LXCommand.java:4857-4878

#### `LXCommand.Osc.RemoveOutput`
- **Constructor**: `public RemoveOutput(LXOscConnection.Output output)`
- **Mutates**: Removes an OSC output connection and stores its JSON for undo.
- **Cite**: LXCommand.java:4880-4906

---

### Category: Midi
Actions for MIDI mapping and template management.

#### `LXCommand.Midi.AddMapping`
- **Constructor**: `public AddMapping(LXShortMessage message, LXNormalizedParameter parameter)`
- **Mutates**: Creates a MIDI mapping from a MIDI message to a parameter. Mapping is created on perform.
- **Cite**: LXCommand.java:4911-4937

#### `LXCommand.Midi.RemoveMapping`
- **Constructor**: `public RemoveMapping(LX lx, LXMidiMapping mapping)`
- **Mutates**: Removes a MIDI mapping and can move it to a new parameter path if component relocates.
- **Cite**: LXCommand.java:4939-4967

#### `LXCommand.Midi.AddTemplate`
- **Constructor**: `public AddTemplate(Class<? extends LXMidiTemplate> templateClass)`
- **Mutates**: Instantiates and adds a MIDI template to the engine.
- **Cite**: LXCommand.java:4969-5010

#### `LXCommand.Midi.RemoveTemplate`
- **Constructor**: `public RemoveTemplate(LXMidiTemplate midiTemplate)`
- **Mutates**: Removes a MIDI template from the engine and stores JSON for undo.
- **Cite**: LXCommand.java:5012-5048

#### `LXCommand.Midi.MoveTemplate`
- **Constructor**: `public MoveTemplate(LXMidiTemplate midiTemplate, int toIndex)`
- **Mutates**: Moves a MIDI template to a new index in the engine's template list.
- **Cite**: LXCommand.java:5050-5076

---

## Summary of Categories and Counts

- **Parameter** (9 action classes): Reset, SetValue, SetIndex, SetColor, Increment, Decrement, Toggle, SetNormalized, SetString, (+ MultiSetValue base)
- **Channel** (10 actions): SetFader, AddPattern, RemovePattern, RemovePatterns, GroupPatterns, ReloadPattern, MovePattern, GoPattern, PatternCycle, AddEffect, RemoveEffect, ReloadEffect, MoveEffect, RelocateEffect
- **Device** (3 actions): LoadPreset, SetRemoteControls, ClearRemoteControls
- **Mixer** (8 actions): AddChannel, MoveChannel, DropChannel, RemoveChannel, RemoveSelectedChannels, Ungroup, UngroupChannel, GroupSelectedChannels, AutoMute
- **Modulation** (8 actions): AddModulator, MoveModulator, RemoveModulator, AddModulation, RemoveModulation, RemoveModulations, AddTrigger, RemoveTrigger, Remove
- **Palette** (7 actions): AddColor, RemoveColor, SaveSwatch, RemoveSwatch, MoveSwatch, SetSwatch, ImportSwatches
- **Snapshots** (9 actions): AddSnapshot, MoveSnapshot, RemoveSnapshot, Update, Recall, RecallImmediate, RemoveView, RemoveViews, UpdateView
- **Structure** (9 actions): AddFixture, RemoveFixture, RemoveSelectedFixtures, MoveFixture, NewModel, ArrangeFixtures, ModifyFixturePositions, AddView, RemoveView, MoveView, ImportViews
- **Clip** (14 actions): Add, Remove, Record, SetMarker, MoveMarker, MoveLane, RemoveClipLane, Event.Remove, Event.RemoveRange, Event.SetCursors, Event.Midi.RemoveNote, Event.Midi.SetVelocity, Event.Midi.SetChannel, Event.Midi.EditNote, Event.Midi.InsertNote, (+ Event.Pattern.*)
- **Osc** (4 actions): AddInput, RemoveInput, AddOutput, RemoveOutput
- **Midi** (5 actions): AddMapping, RemoveMapping, AddTemplate, RemoveTemplate, MoveTemplate

**Total: ~90+ action classes across 11 categories.**

---

## Supporting Types and References

### Parameter-Related
- `ParameterReference<T extends LXParameter>`: Stores parameter by component ID + path for persistence across undo/redo (LXCommand.java:138-178)
- `ComponentReference<T extends LXComponent>`: Stores component by ID, dereferences on access (LXCommand.java:122-136)
- Supported parameter types: `DiscreteParameter`, `LXParameter`, `LXNormalizedParameter`, `StringParameter`, `ColorParameter`, `BooleanParameter`

### Pattern/Effect References
- `LXPattern`, `LXEffect`, `LXPatternEngine`, `LXEffect.Container`, `LXPatternEngine.Container`
- `PatternRack`: Special pattern type for grouping patterns
- Reference: LXCommand.java imports at lines 78-88

### Modulation References
- `LXCompoundModulation.Target`: Target for modulation (parameter or component)
- `LXParameterModulation`, `LXCompoundModulation`, `LXTriggerModulation`
- `ModulationSourceReference`: Inner class handling source as either parameter or component (LXCommand.java:2266-2290)
- Reference: LXModulationEngine imports at line 66

### Channel/Mixer References
- `LXChannel`, `LXAbstractChannel`, `LXGroup`, `LXBus`, `LXMixerEngine`
- Reference: LXCommand.java imports at lines 58-62

### Fixture/Structure References
- `LXFixture`, `JsonFixture`, `LXStructure`, `LXViewDefinition`
- Reference: LXCommand.java imports at lines 84-87

### Clip References
- `LXClip`, `LXChannelClip`, `LXClipLane<T>`, `LXClipEvent<T>`
- `MidiNoteClipLane`, `MidiNoteClipEvent`, `ParameterClipLane`, `ParameterClipEvent`, `PatternClipLane`, `PatternClipEvent`
- `Cursor`: Timing class for clip events
- Reference: LXCommand.java imports at lines 37-47

### MIDI References
- `LXMidiMapping`, `LXShortMessage`, `LXMidiEngine`, `MidiNote`
- Reference: LXCommand.java imports at lines 53-56

### Snapshot References
- `LXSnapshot`, `LXGlobalSnapshot`, `LXClipSnapshot`, `LXSnapshotEngine`
- Reference: LXCommand.java imports at lines 80-83

### OSC References
- `LXOscConnection.Input`, `LXOscConnection.Output`
- Reference: LXCommand.java import at line 70

### Serialization
- `JsonObject`: Gson JSON object for storing state; used throughout for undo/redo serialization
- `LXSerializable.Utils.toObject(...)`: Converts components to JSON
- Reference: LXCommand.java imports at line 29 and method usage throughout

---

## Planned-Tool Reconnaissance

### Tool: `add_channel`
**Mapping**: `LXCommand.Mixer.AddChannel` is the exact match.
- **Constructor options**: Can create empty, with pattern class, or from JSON object
- **Related engine**: `LXMixerEngine.addChannel()` — cite: imports at line 62
- **Side effects**: Sets focus and selection on the new channel
- **Citation**: LXCommand.java:1754-1819

### Tool: `remove_channel`
**Mapping**: `LXCommand.Mixer.RemoveChannel` (individual) or `LXCommand.Mixer.RemoveSelectedChannels` (batch).
- **Recursive**: Removing a group automatically removes its children
- **Cleanup**: Removes associated modulations, MIDI mappings, snapshot views
- **Citation**: LXCommand.java:1890-1950, 1952-1994

### Tool: `set_parameter` (generic parameter by path/reference)
**Mapping**: Several candidates depending on parameter type:
  - `LXCommand.Parameter.SetValue(LXParameter, double)` — generic float/double parameters
  - `LXCommand.Parameter.SetValue(DiscreteParameter, int)` — discrete int parameters
  - `LXCommand.Parameter.SetNormalized(LXNormalizedParameter, double)` — normalized 0-1 parameters
  - `LXCommand.Parameter.SetString(StringParameter, String)` — string parameters
  - `LXCommand.Parameter.SetColor(ColorParameter, double hue, double saturation)` — color parameters
- **Reference resolution**: `ParameterReference` allows serialization by path for cross-undo stability
- **Citation**: LXCommand.java:432-836

### Tool: `add_modulator`
**Mapping**: `LXCommand.Modulation.AddModulator`
- **Constructor**: Requires modulation engine and modulator class
- **Optional**: Color assignment, JSON object for restoration
- **Citation**: LXCommand.java:2128-2188

### Tool: `add_modulation` (wire source→target)
**Mapping**: `LXCommand.Modulation.AddModulation`
- **Source**: Can be `LXNormalizedParameter` or `LXComponent` (if implements `LXNormalizedParameter`)
- **Target**: `LXCompoundModulation.Target` (wraps target parameter)
- **Related**: `LXParameterModulation.ModulationException` thrown on invalid wiring
- **Citation**: LXCommand.java:2264-2338

### Tool: `remove_modulation`
**Mapping**: `LXCommand.Modulation.RemoveModulation` (single) or `LXCommand.Modulation.RemoveModulations` (batch by target).
- **Also handles**: Triggers (boolean modulations) via `LXCommand.Modulation.RemoveTrigger`
- **Citation**: LXCommand.java:2341-2425, 2464-2517

### Tool: `add_midi_mapping`
**Mapping**: `LXCommand.Midi.AddMapping`
- **Constructor**: Takes `LXShortMessage` and `LXNormalizedParameter`
- **Implementation**: Delegates to `LXMidiMapping.create(lx, message, parameter)`
- **Citation**: LXCommand.java:4911-4937

### Tool: `add_pattern`
**Mapping**: `LXCommand.Channel.AddPattern`
- **Constructor options**: Engine/container, pattern class, optional JSON object, optional index
- **Side effect**: Instantiates pattern via `lx.instantiatePattern(Class)`
- **Related**: `LXPatternEngine.addPattern()`, pattern stored as `ComponentReference` for undo
- **Citation**: LXCommand.java:868-944

### Tool: `remove_pattern`
**Mapping**: `LXCommand.Channel.RemovePattern` (single) or `LXCommand.Channel.RemovePatterns` (batch).
- **Cleanup**: Removes modulations, MIDI mappings, snapshot views, clip lanes, remote controls
- **State preservation**: Stores pattern JSON, index, active/focused flags
- **Citation**: LXCommand.java:946-1047

### Tool: `add_effect`
**Mapping**: `LXCommand.Channel.AddEffect`
- **Container**: Any `LXComponent` that implements `LXEffect.Container` (channels, patterns, etc.)
- **Constructor**: Takes parent component and effect class
- **Validation**: `validateEffectContainer()` ensures parent is valid
- **Citation**: LXCommand.java:1358-1403

### Tool: `remove_effect`
**Mapping**: `LXCommand.Channel.RemoveEffect`
- **Cleanup**: Cascades to modulations, MIDI mappings, snapshot views, clip lanes
- **Validation**: Checks `effect.locked` flag to prevent removal of locked effects
- **Citation**: LXCommand.java:1405-1451

---

## Phase-2 Introspection Surface

LX exposes several mechanisms for a future agent to read pattern/effect metadata and source code:

### 1. **Annotation-Based Metadata**
Located in `/Users/danoved/Source/LX/src/main/java/heronarts/lx/`, these annotations provide component metadata:

#### `LXComponent.Name` (inner annotation)
- **Location**: LXComponent.java:80
- **Purpose**: Runtime-readable name override for a component class
- **Retention**: RUNTIME

#### `LXComponent.Description` (inner annotation)
- **Location**: LXComponent.java:98
- **Purpose**: Human-readable description of component's purpose
- **Retention**: RUNTIME

#### `LXComponent.Author` (inner annotation)
- **Location**: LXComponent.java:108
- **Purpose**: Component author attribution
- **Retention**: RUNTIME

#### `LXComponent.Tags` (inner annotation)
- **Location**: LXComponent.java:118
- **Purpose**: Arbitrary string tags for categorization
- **Retention**: RUNTIME

#### `LXComponent.Hidden` (inner annotation)
- **Location**: LXComponent.java:87
- **Purpose**: Mark component as hidden from UI enumeration
- **Retention**: RUNTIME

#### `LXComponent.PluginRequired` (inner annotation)
- **Location**: LXComponent.java:128
- **Purpose**: Declare required plugin dependency
- **Retention**: RUNTIME

#### `@LXCategory` (standalone)
- **Location**: LXCategory.java
- **Categories**: CORE, FORM, COLOR, MIDI, STRIP, TEXTURE, TRIGGER, TEST, OTHER, AUDIO, MACRO, DMX
- **Retention**: RUNTIME
- **Applied to**: Pattern and effect classes

#### `@LXComponentName` (deprecated, still present)
- **Location**: LXComponentName.java
- **Note**: Superseded by `@LXComponent.Name`
- **Retention**: RUNTIME

### 2. **Registry Interface**
- **Class**: `LXRegistry` (LXRegistry.java)
- **Purpose**: Central registry of all pattern/effect/modulator/fixture classes available
- **Key interfaces**:
  - `LXRegistry.Listener` — callbacks for content changes (contentChanged, fixturesChanged, channelBlendsChanged, etc.)
  - Maintains lists of registered pattern, effect, modulator, fixture, blend classes
  - Internal pattern/effect lists available via static DEFAULT_PATTERNS, DEFAULT_EFFECTS, etc. (lines 144+)
- **Citation**: LXRegistry.java:78-150+

### 3. **Class Loader Introspection**
- **Class**: `LXClassLoader` (LXClassLoader.java)
- **Purpose**: Scans JAR files in content directory; parses and registers extension components
- **Metadata extracted**:
  - Class type (pattern, effect, modulator, fixture, plugin)
  - Component name (from `@LXComponent.Name` or class name)
  - Category (from `@LXCategory`)
  - Author, version, LX compatibility version
- **Plugin trust**: Validates plugin SHA256 against TRUSTED_PACKAGES list (line 63)
- **Citation**: LXClassLoader.java:45-100+

### 4. **Component Base Class Reflection**
- **Class**: `LXComponent` (LXComponent.java)
- **Introspection methods** (to be determined by reading the full file, but implied):
  - `getComponentName(Class<?>)` — static method to extract display name
  - Parameter registry via `getParameter(String path)` — can walk parameter tree
  - Label, description via standard JavaBean/annotation introspection
- **Parameter types**: All parameters are instances of `LXParameter` hierarchy; can inspect `label`, `description` properties
- **Citation**: LXComponent.java (full enumeration of introspection surface would require reading full file)

### 5. **Pattern/Effect Source Code Access**
- **No direct source code embedding**: LX stores patterns/effects as .class bytecode in JARs or compiled in the main LX JAR
- **Decompilation path**: A tool could use standard Java bytecode decompilers (e.g., CFR, JD-GUI) on loaded classes, but this is outside LX's introspection surface
- **Annotation inspection**: Class-level and method-level annotations are the primary source of algorithmic metadata
- **No AST/metadata surface**: LX does not expose an AST or structured algorithm description beyond parameter lists and annotations

### 6. **Parameter Introspection**
- **Parameter tree walkable**: Every `LXComponent` (pattern, effect, modulator) has registered parameters
- **Parameter metadata**:
  - `label`: Human-readable name
  - `description`: Tooltip/help text
  - `min`, `max` (for bounded parameters)
  - `index`, `value` (for discrete parameters)
  - Current value, normalized value (0-1)
- **Parameter types**: `LXParameter` hierarchy includes `BoundedParameter`, `DiscreteParameter`, `BooleanParameter`, `StringParameter`, `ColorParameter`, etc.
- **Citation**: Parameter package imports at LXCommand.java:71-77

---

## Gaps and Could-Not-Determine

1. **Full constructor signatures for Clip.Event.Pattern classes**: The file excerpt did not include the complete definition of `LXCommand.Clip.Event.Pattern.*` classes. Based on parallel with MIDI classes, they likely have InsertEvent, RemoveEvent, SetReference classes, but exact constructors were not fully read.

2. **LXComponent full introspection API**: The full interface of `LXComponent` for parameter walking, listener registration, and metadata extraction would need the full file read. Assumed but not confirmed: `getParameter(String path)`, parameter iteration methods.

3. **LXModulationEngine path-resolution internals**: The exact mechanism for `LXParameterModulation.move(...)` and path string transformation is referenced but not fully detailed in the LXCommand file (delegated to modulation engine).

4. **JsonObject structure for serialization**: LX uses Gson `JsonObject` for all undo/redo serialization. The exact JSON keys and nested structure (e.g., what fields are stored for a pattern, modulation, etc.) would require reading LXSerializable implementation and specific component serialize/deserialize methods.

5. **Parameter path syntax**: The exact string syntax for canonical parameter paths (e.g., `"channel.3.pattern.2.brightness"`) is used throughout but not formally documented in LXCommand.java. Inferred from `LXPath` class usage (imported at line 34) but not enumerated here.

6. **Component instantiation**: `lx.instantiatePattern()`, `lx.instantiateEffect()`, `lx.instantiateModulator()`, `lx.instantiateFixture()` are delegated to LX engine. The exact instantiation mechanism (class loader, reflection, error handling) is not in LXCommand.java.

7. **Clip event type hierarchy**: The generic `LXClipEvent<T>` and `LXClipLane<T>` use type parameters. The full type hierarchy (`ParameterClipEvent`, `MidiNoteClipEvent`, `PatternClipEvent`) is mentioned but not fully enumerated with signatures in the sections read.

---

## Conclusion

LXCommand.java is the authoritative surface for undo-aware mutations in LX. It contains 90+ action classes across 11 categories, with constructor-based configuration for flexibility. Each action class uses `ComponentReference` and `ParameterReference` for stable cross-undo lookup, `JsonObject` for state serialization, and private implement of `perform()` and `undo()` to maintain bidirectional state.

Planned MCP tools map cleanly onto existing LXCommand classes. Phase-2 capability for reading pattern/effect algorithms exists via runtime annotations (`@LXComponent.Name`, `@LXComponent.Description`, `@LXCategory`, etc.) and class loader introspection (`LXRegistry`, `LXClassLoader`), but source code access requires decompilation outside LX's introspection surface.

