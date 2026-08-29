package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXSerializable;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.PatternClipLane;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulation.LXTriggerModulation;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.pattern.LXPattern;

/**
 * Read-only snapshots and mutations of the mixer. Snapshot records are immutable copies
 * assembled on the engine thread, so tool handlers never hold live LX objects off-thread.
 * Mutation primitives are also called on the engine thread. They route through LXCommand
 * except where LX exposes no explicit-argument command, as documented on that primitive.
 */
public final class Channels {

  public enum BusType { CHANNEL, GROUP }

  public enum PatternMode { PLAYLIST, BLEND }

  /**
   * {@code active} is the PLAYLIST-mode notion of "current pattern" (meaningless in
   * BLEND mode, where every pattern renders concurrently). {@code contributing} is the
   * mode-correct visibility rule: in PLAYLIST mode it mirrors {@code active}; in BLEND
   * mode it is {@code enabled && compositeLevel > 0}. {@code nestedPatternCount} is nonzero
   * when this pattern is itself an {@link LXPatternEngine.Container} (e.g. a
   * {@code PatternRack}) — its own child patterns are not walked here (see issue #117);
   * a nonzero count is a marker that more structure exists at this path, not a
   * traversal. {@code hasLocalModulation} flags a nonempty device-local modulation
   * engine (see {@link #hasLocalModulation(LXDeviceComponent)}).
   */
  public record PatternInfo(String path, int id, String label, String className, boolean active,
      boolean enabled, double compositeLevel, boolean contributing, int nestedPatternCount,
      boolean hasLocalModulation, Views.ViewRef view, List<EffectInfo> effects) {}

  /** {@code hasLocalModulation}: see {@link #hasLocalModulation(LXDeviceComponent)}. */
  public record EffectInfo(String path, int id, String label, String className, boolean enabled,
      boolean hasLocalModulation, Views.ViewRef view) {}

  /** A single parameter's live value plus its canonical path, for compact performance-surface fields. */
  public record Field<T>(T value, String path) {}

  /**
   * A selector parameter's current option label plus its path. {@code options} is the full
   * list of option labels when this occurrence owns them, or null when the option set is
   * shared across every channel and reported once at the mixer level instead (compactness).
   */
  public record EnumField(String current, List<String> options, String path) {}

  /**
   * Auto-cycle and transition settings, owned by a channel's {@link LXPatternEngine} — only
   * present for channels in PLAYLIST/BLEND mode (groups and the master bus have no pattern
   * engine). {@code transitionBlendMode}'s options are shared across channels; see
   * {@link MixerControls#transitionBlendModeOptions()}.
   */
  public record PatternEngineControls(Field<Boolean> autoCycleEnabled, EnumField autoCycleMode,
      Field<Double> autoCycleTimeSecs, Field<Boolean> transitionEnabled,
      Field<Double> transitionTimeSecs, EnumField transitionBlendMode) {}

  /**
   * The mixer-performance surface on a single channel: crossfade group assignment, blend
   * mode, auto-mute, cue/aux preview state, and (for pattern-hosting channels) auto-cycle /
   * transition settings. {@code blendMode}'s options are shared across channels; see
   * {@link MixerControls#blendModeOptions()}.
   */
  public record ChannelControls(EnumField crossfadeGroup, EnumField blendMode,
      Field<Boolean> autoMute, Field<Boolean> isAutoMuted, Field<Boolean> cueActive,
      Field<Boolean> auxActive, PatternEngineControls patternEngine) {}

  /**
   * {@code groupPath} is the canonical path of the enclosing group, or null at top level
   * — the mixer's channel list is flat, with group members as siblings of their group.
   * {@code patternMode} channels in BLEND mode render multiple patterns at once, so the
   * single {@code active} flag on patterns is only meaningful in PLAYLIST mode.
   * {@code containerPatternCount} and {@code anyLocalModulation} are channel-level
   * rollups, present at every detail level (including summary, where per-pattern markers
   * are otherwise only visible via {@code activePattern} in PLAYLIST mode — see issue
   * #117): {@code containerPatternCount} is how many of this channel's direct patterns
   * are themselves an {@link LXPatternEngine.Container} (0 for ordinary channels; distinct
   * from a pattern's own {@code nestedPatternCount}), and {@code anyLocalModulation} is
   * true iff any pattern or effect on this channel has a nonempty local modulation engine.
   * Both are markers that hidden structure exists somewhere on the channel, not the data
   * itself — use {@code detail: full} or a per-pattern path to find which pattern/effect.
   * {@code view} is this channel's model-view assignment; see {@link Views.ViewRef}. Every
   * pattern and effect on the channel carries its own {@code view} too — a pattern/effect
   * can override its channel's view, so the channel-level assignment alone doesn't say what
   * a specific pattern renders to.
   */
  public record ChannelInfo(String path, int id, String label, int index, BusType type,
      boolean enabled, double fader, String groupPath, PatternMode patternMode,
      List<PatternInfo> patterns, List<EffectInfo> effects, ChannelControls controls,
      int containerPatternCount, boolean anyLocalModulation, Views.ViewRef view) {}

  /**
   * {@code anyLocalModulation}: the master bus can only host effects (no patterns), so
   * this rolls up {@code hasLocalModulation} across {@code effects} only — see
   * {@link ChannelInfo#anyLocalModulation()} for the channel-level equivalent.
   */
  public record MasterInfo(String path, int id, String label, double fader,
      List<EffectInfo> effects, boolean anyLocalModulation) {}

  /**
   * Mixer-wide performance controls: the crossfader (0 = full A, 1 = full B — channels join
   * group A/B via their {@code crossfadeGroup}), its blend mode, and the cue/aux preview
   * buses (cue = primary preview, aux = secondary preview). {@code blendModeOptions} /
   * {@code transitionBlendModeOptions} are the shared option sets referenced by every
   * channel's {@code blendMode} / {@code patternEngine.transitionBlendMode} — sourced from
   * the first channel of the matching kind, so both are null on a mixer with no channels.
   */
  public record MixerControls(Field<Double> crossfader, EnumField crossfaderBlendMode,
      Field<Boolean> cueA, Field<Boolean> cueB, Field<Boolean> auxA, Field<Boolean> auxB,
      List<String> blendModeOptions, List<String> transitionBlendModeOptions) {}

  public record MixerInfo(List<ChannelInfo> channels, MasterInfo master, MixerControls controls) {}

  private Channels() {}

  /** Call on the engine thread; the returned records are safe to read anywhere. */
  public static MixerInfo list(LX lx) {
    List<ChannelInfo> channels = new ArrayList<>();
    for (LXAbstractChannel channel : lx.engine.mixer.channels) {
      channels.add(describe(channel));
    }
    return new MixerInfo(channels, describeMaster(lx.engine.mixer.masterBus),
        mixerControls(lx.engine.mixer));
  }

  private static MixerControls mixerControls(LXMixerEngine mixer) {
    // blendMode / transitionBlendMode option sets are instantiated per-channel but always
    // drawn from the same registry class list — report them once here (see the tool-facing
    // docs on ChannelControls) instead of repeating an identical array per channel.
    List<String> blendModeOptions = null;
    List<String> transitionBlendModeOptions = null;
    for (LXAbstractChannel channel : mixer.channels) {
      if (blendModeOptions == null) {
        blendModeOptions = objectOptions(channel.blendMode);
      }
      if (transitionBlendModeOptions == null && channel instanceof LXChannel c) {
        transitionBlendModeOptions = objectOptions(c.getPatternEngine().transitionBlendMode);
      }
      if (blendModeOptions != null && transitionBlendModeOptions != null) {
        break;
      }
    }
    return new MixerControls(
        new Field<>(mixer.crossfader.getValue(), Resolve.canonicalPathOrNull(mixer.crossfader)),
        new EnumField(mixer.crossfaderBlendMode.getObject().getLabel(),
            objectOptions(mixer.crossfaderBlendMode), Resolve.canonicalPathOrNull(mixer.crossfaderBlendMode)),
        new Field<>(mixer.cueA.isOn(), Resolve.canonicalPathOrNull(mixer.cueA)),
        new Field<>(mixer.cueB.isOn(), Resolve.canonicalPathOrNull(mixer.cueB)),
        new Field<>(mixer.auxA.isOn(), Resolve.canonicalPathOrNull(mixer.auxA)),
        new Field<>(mixer.auxB.isOn(), Resolve.canonicalPathOrNull(mixer.auxB)),
        blendModeOptions,
        transitionBlendModeOptions);
  }

  private static List<String> objectOptions(ObjectParameter<LXBlend> blendParameter) {
    List<String> options = new ArrayList<>();
    for (LXBlend blend : blendParameter.getObjects()) {
      options.add(blend.getLabel());
    }
    return options;
  }

  // ── Channel mutations ────────────────────────────────────────────────────────

  /**
   * Resolve a pattern class name against the LX registry. Accepts the full class name, or
   * the short name ({@code getSimpleName()} / display name) advertised by
   * {@code list_available_patterns}.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for an unregistered or ambiguous name.
   */
  public static Class<? extends LXPattern> resolvePatternClass(LX lx, String className) {
    return Resolve.resolveClassName(lx.registry.patterns, className, Resolve.Failure.TYPE_MISMATCH,
        "Unknown pattern type: " + className + " (see list_available_patterns)");
  }

  /**
   * Resolve an effect class name against the LX registry. Accepts the full class name, or
   * the short name ({@code getSimpleName()} / display name) advertised by
   * {@code list_available_effects}.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for an unregistered or ambiguous name.
   */
  public static Class<? extends LXEffect> resolveEffectClass(LX lx, String className) {
    return Resolve.resolveClassName(lx.registry.effects, className, Resolve.Failure.TYPE_MISMATCH,
        "Unknown effect type: " + className + " (see list_available_effects)");
  }

  /**
   * Add a channel to the mixer, optionally pre-populated with a pattern.
   *
   * @param patternClass optional first pattern class; null leaves the channel empty
   */
  public static LXChannel addChannel(LX lx, Class<? extends LXPattern> patternClass) {
    List<LXAbstractChannel> channels = lx.engine.mixer.channels;
    int before = channels.size();
    LXCommand.Mixer.AddChannel cmd = (patternClass != null)
        ? new LXCommand.Mixer.AddChannel(patternClass)
        : new LXCommand.Mixer.AddChannel();
    Commands.perform(lx, cmd);
    if (channels.size() != before + 1 || !(channels.get(before) instanceof LXChannel channel)) {
      throw new IllegalStateException("AddChannel did not add a new LXChannel");
    }
    return channel;
  }

  /**
   * Remove a channel by path.
   *
   * @throws Resolve.ResolveException NOT_FOUND or TYPE_MISMATCH if path does not
   *     resolve to an LXAbstractChannel.
   */
  public static void removeChannel(LX lx, String path) {
    LXAbstractChannel channel = Resolve.component(lx, path, LXAbstractChannel.class);
    Commands.perform(lx, new LXCommand.Mixer.RemoveChannel(channel));
  }

  /** A newly created group plus all mixer component paths changed by its insertion. */
  public record GroupChannelsResult(LXGroup group, List<LXChannel> channels,
      List<PathChange> oscChanges) {}

  /** A dissolved group plus the now-top-level channels and all changed mixer paths. */
  public record UngroupChannelsResult(int groupId, String groupPath, List<LXChannel> channels,
      List<PathChange> oscChanges) {}

  /** One channel removed from a group plus all changed mixer paths. */
  public record UngroupChannelResult(LXChannel channel, int groupId, String groupPath,
      List<PathChange> oscChanges) {}

  /**
   * Create a group from an explicit list of top-level channel paths.
   *
   * <p>LX has no explicit-list grouping command: GroupSelectedChannels reads UI selection.
   * This therefore calls the engine API directly and is not undoable. Every path is resolved
   * and validated before mutation so a bad request cannot leave a partially built group.
   */
  public static GroupChannelsResult groupChannels(LX lx, List<String> paths) {
    if (paths.isEmpty()) {
      throw Resolve.invalidArgument("paths must contain at least one channel path");
    }
    List<LXChannel> channels = new ArrayList<>();
    Set<Integer> ids = new LinkedHashSet<>();
    for (String path : paths) {
      LXChannel channel = Resolve.component(lx, path, LXChannel.class);
      if (!ids.add(channel.getId())) {
        throw Resolve.invalidArgument("Duplicate channel path: " + path);
      }
      if (channel.getGroup() != null) {
        throw Resolve.invalidArgument("Channel is already in a group: " + path);
      }
      channels.add(channel);
    }

    // LXMixerEngine.addGroup(List) was built for getSelectedChannelsForGroup(), whose
    // result is always mixer-ordered. Supplying a later channel before an earlier one can
    // make its internal insertion index exceed the shrinking list size, after it has already
    // moved a member. Normalize explicit MCP input to that engine precondition.
    channels.sort(Comparator.comparingInt(LXChannel::getIndex));

    var before = snapshotPaths(lx.engine.mixer.channels);
    LXGroup group = lx.engine.mixer.addGroup(channels);
    if (group == null || group.channels.size() != channels.size()
        || !group.channels.containsAll(channels)) {
      throw new IllegalStateException("Mixer did not create the requested channel group");
    }
    lx.command.setDirty(true);
    return new GroupChannelsResult(group, List.copyOf(channels), pathChanges(lx, before));
  }

  /** Dissolve a group through LXCommand so the operation is undoable. */
  public static UngroupChannelsResult ungroupChannels(LX lx, String path) {
    LXGroup group = Resolve.component(lx, path, LXGroup.class);
    int groupId = group.getId();
    String groupPath = group.getCanonicalPath();
    List<LXChannel> channels = List.copyOf(group.channels);
    var before = snapshotPaths(lx.engine.mixer.channels);
    Commands.perform(lx, new LXCommand.Mixer.Ungroup(group));
    if (lx.getComponent(groupId) != null
        || channels.stream().anyMatch(channel -> channel.getGroup() != null)) {
      throw new IllegalStateException("Ungroup did not dissolve group " + groupPath);
    }
    return new UngroupChannelsResult(groupId, groupPath, channels, pathChanges(lx, before));
  }

  /** Remove one member from its group through LXCommand so the operation is undoable. */
  public static UngroupChannelResult ungroupChannel(LX lx, String path) {
    LXChannel channel = Resolve.component(lx, path, LXChannel.class);
    LXGroup group = channel.getGroup();
    if (group == null) {
      throw Resolve.invalidArgument("Channel is not in a group: " + path);
    }
    int groupId = group.getId();
    String groupPath = group.getCanonicalPath();
    var before = snapshotPaths(lx.engine.mixer.channels);
    Commands.perform(lx, new LXCommand.Mixer.UngroupChannel(channel));
    if (channel.getGroup() != null) {
      throw new IllegalStateException("UngroupChannel did not remove channel " + path);
    }
    return new UngroupChannelResult(channel, groupId, groupPath, pathChanges(lx, before));
  }

  /**
   * Add a pattern of {@code patternClass} to the pattern engine at {@code containerPath}.
   * The container may be a channel or a nested pattern host such as {@code PatternRack}.
   * When this is the first pattern added via MCP (the engine was empty), the pattern
   * auto-activates because LX's engine treats the first pattern as active.
   *
   * @param index 0-based insertion index, or -1 to append
   */
  public static LXPattern addPattern(LX lx, String containerPath,
      Class<? extends LXPattern> patternClass, int index) {
    return insertPattern(lx, containerPath, patternClass, null, index);
  }

  /**
   * The one insertion path. {@code patternObj} null builds a blank instance
   * ({@link #addPattern}); non-null loads serialized state into it ({@link #copyPattern}) —
   * the same distinction LX draws with its two {@code AddPattern} constructors, so the
   * index and validation semantics cannot drift between adding and copying.
   */
  private static LXPattern insertPattern(LX lx, String containerPath,
      Class<? extends LXPattern> patternClass, JsonObject patternObj, int index) {
    LXComponent component = Resolve.component(lx, containerPath);
    if (!(component instanceof LXPatternEngine.Container container)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Not a channel or pattern-engine container at path: " + containerPath
              + " (found " + component.getClass().getSimpleName() + ")");
    }
    LXPatternEngine engine = container.getPatternEngine();
    int before = engine.patterns.size();
    // LX's addPattern throws on index > size INSIDE perform(), which swallows it and
    // wipes the undo stack — reject up front like the other index-taking primitives.
    if (index > before) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Pattern index " + index + " out of range [0," + before + "] on " + containerPath);
    }
    Commands.perform(lx,
        new LXCommand.Channel.AddPattern(engine, patternClass, patternObj, index));
    if (engine.patterns.size() != before + 1) {
      throw new IllegalStateException("AddPattern did not add a pattern to " + containerPath);
    }
    // Inserted patterns land at their target index; new appended patterns are last.
    int resultIndex = (index >= 0 && index <= before) ? index : before;
    return engine.patterns.get(resultIndex);
  }

  /**
   * A reference from outside a pattern's own subtree that points into it. On a copy these
   * are the wirings the new instance does <em>not</em> carry: they address the source by
   * canonical path or component id, and only the source keeps them.
   *
   * <p>{@code kind} is one of {@code modulation}, {@code trigger}, {@code midiMapping},
   * {@code snapshotView}, {@code clipLane}, {@code clipPatternEvent}. {@code scope} is the
   * canonical path of the owning modulation engine, or of the MIDI/snapshot/clip holder.
   * {@code sourcePath} is null for kinds that have no driving parameter. {@code midi} is
   * populated only for {@code midiMapping}: LX's label alone cannot be fed back into
   * add_midi_mapping, so the type/channel/number identity travels with the report.
   */
  public record ExternalReference(String kind, String scope, String sourcePath,
      String targetPath, Midi.Binding midi) {

    ExternalReference(String kind, String scope, String sourcePath, String targetPath) {
      this(kind, scope, sourcePath, targetPath, null);
    }
  }

  /**
   * The pattern created by {@link #copyPattern}, plus every external reference into the
   * source that the copy does not reproduce.
   */
  public record PatternCopyResult(LXPattern pattern, List<ExternalReference> unreplicatedWiring) {}

  /**
   * Copy a configured pattern into {@code containerPath}, which may be any channel or
   * pattern-engine container including the source's own.
   *
   * <p>The copy is made by serializing the source with ids stripped and loading that JSON
   * into a fresh instance ({@link LXCommand.Channel.AddPattern} with a pattern object —
   * the round-trip LX itself documents for copy/paste). Everything inside the pattern's
   * own subtree travels: parameter values, nested rack patterns and their effects, and the
   * pattern's device-local modulation engine, whose wirings serialize as paths relative to
   * their scope and so re-resolve against the copy rather than the source.
   *
   * <p>Nothing outside that subtree travels — channel-level and global modulations,
   * triggers, MIDI mappings and snapshot views address the source specifically. They are
   * returned in {@link PatternCopyResult#unreplicatedWiring()} rather than silently
   * dropped, so a caller performing a move (copy, then {@link #removePattern}) knows
   * exactly what it must rewire.
   *
   * @param index 0-based insertion index, or -1 to append
   * @throws Resolve.ResolveException TYPE_MISMATCH if the destination is not a container,
   *     is the source pattern or a descendant of it, or the index is out of range
   */
  public static PatternCopyResult copyPattern(LX lx, String sourcePath, String containerPath,
      int index) {
    LXPattern source = Resolve.component(lx, sourcePath, LXPattern.class);
    LXComponent component = Resolve.component(lx, containerPath);
    // Copying into itself would nest a stale snapshot of the source inside the source.
    if (component == source || component.isDescendant(source)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Destination " + containerPath + " is inside the pattern being copied ("
              + sourcePath + ")");
    }
    LXPattern copy = insertPattern(lx, containerPath, source.getClass(),
        LXSerializable.Utils.toObject(source, true), index);
    return new PatternCopyResult(copy, externalReferences(lx, source));
  }


  /**
   * Every reference into {@code component} held outside its own subtree. Ancestor modulation
   * engines are walked upward from the parent, so the component's own modulation engine —
   * which travels with a copy — is deliberately excluded. Shared by the pattern, effect and
   * channel copy primitives; what counts as "outside" differs entirely between them (a
   * channel owns its channel-level engine, a pattern does not), and this walk gets that
   * right for each without special-casing.
   */
  private static List<ExternalReference> externalReferences(LX lx, LXComponent component) {
    List<ExternalReference> references = new ArrayList<>();
    modulationReferences(component, engine -> true, references);
    var mappings = lx.engine.midi.findMappings(component);
    if (mappings != null) {
      for (var mapping : mappings) {
        references.add(new ExternalReference("midiMapping",
            lx.engine.midi.getCanonicalPath(), null,
            mapping.parameter.getCanonicalPath(), Midi.binding(mapping)));
      }
    }
    var views = lx.engine.snapshots.findSnapshotViews(component);
    if (views != null) {
      for (var view : views) {
        references.add(new ExternalReference("snapshotView",
            view.getSnapshot().getCanonicalPath(), null, view.getViewPath()));
      }
    }
    collectClipReferences(lx, component, references);
    remoteControlReferences(component, references);
    return references;
  }

  /**
   * Wirings held by an ancestor modulation engine that address {@code component}. The walk
   * starts at the parent, so the component's own engine — which travels with a copy and
   * survives a move — is never reported.
   *
   * <p>{@code include} selects which engines count, and receives the live engine rather
   * than its path: the move case needs to ask whether an engine can still reach the
   * component after it lands somewhere else, and re-resolving a scope string to answer that
   * would be both slower and silently wrong if the path failed to resolve.
   */
  private static void modulationReferences(LXComponent component,
      Predicate<LXModulationEngine> include, List<ExternalReference> references) {
    for (LXComponent parent = component.getParent(); parent != null; parent = parent.getParent()) {
      if (!(parent instanceof LXModulationContainer modulationContainer)) {
        continue;
      }
      LXModulationEngine engine = modulationContainer.getModulationEngine();
      if (!include.test(engine)) {
        continue;
      }
      String scope = Resolve.canonicalPath(engine);
      List<LXCompoundModulation> modulations =
          engine.findModulations(component, engine.modulations);
      if (modulations != null) {
        for (LXCompoundModulation modulation : modulations) {
          references.add(new ExternalReference("modulation", scope,
              Resolve.canonicalPath(modulation.source), Resolve.canonicalPath(modulation.target)));
        }
      }
      List<LXTriggerModulation> triggers = engine.findModulations(component, engine.triggers);
      if (triggers != null) {
        for (LXTriggerModulation trigger : triggers) {
          references.add(new ExternalReference("trigger", scope,
              Resolve.canonicalPath(trigger.source), Resolve.canonicalPath(trigger.target)));
        }
      }
    }
  }

  /**
   * Timeline automation that addresses {@code component} from a clip outside it. A clip
   * belongs to a bus; when that bus is the component being copied (a channel copy) its
   * lanes travel with the copy and are not external, so those are skipped — the same
   * inside/outside test the modulation walk applies.
   *
   * <p>Two kinds are reported: {@code clipLane}, a lane automating a parameter in the
   * subtree, and {@code clipPatternEvent}, a pattern-launch lane whose events name a copied
   * pattern. LX's own {@code RemoveComponent} tracks both, which is why a copy that ignored
   * them would report an empty {@code unreplicatedWiring} while silently losing automation.
   */
  private static void collectClipReferences(LX lx, LXComponent component,
      List<ExternalReference> references) {
    List<LXClip> clips = new ArrayList<>();
    List<LXBus> buses = new ArrayList<>(lx.engine.mixer.channels);
    buses.add(lx.engine.mixer.masterBus);
    for (LXBus bus : buses) {
      if (bus == component || bus.isDescendant(component)) {
        continue;
      }
      for (LXClip clip : bus.clips) {
        // Sparse by design — an empty grid clip slot is a null entry.
        if (clip != null) {
          clips.add(clip);
        }
      }
    }
    // The arrange composition is an LXClip in its own right (LXComposition extends LXClip)
    // and is add_clip_lane's default target, so lanes automating the copied subtree
    // commonly live here rather than on any bus.
    LXComposition composition = lx.engine.timeline.getComposition();
    if (composition != null) {
      clips.add(composition);
    }
    // findClipLanes matches parameter automation anywhere in the subtree; pattern-launch
    // events name a pattern directly, so every pattern under the component counts — a
    // copied channel strands the launch events for its patterns just as a copied pattern
    // strands its own.
    List<LXPattern> patterns = subtreePatterns(component);
    for (LXClip clip : clips) {
      String clipPath = Resolve.canonicalPath(clip);
      List<LXClipLane<?>> lanes = clip.findClipLanes(component);
      if (lanes != null) {
        for (LXClipLane<?> lane : lanes) {
          // Empty lanes carry nothing to strand. This matters because LX auto-creates a
          // full set of structural lanes per channel on the arrange composition (bus,
          // pattern, midi, palette, global-modulation), all empty — reporting those would
          // put entries in unreplicatedWiring for every copy in every project.
          if (!lane.events.isEmpty()) {
            references.add(new ExternalReference("clipLane", clipPath, null,
                Resolve.canonicalPath(lane)));
          }
        }
      }
      for (LXClipLane<?> lane : clip.lanes) {
        if (!(lane instanceof PatternClipLane patternLane)) {
          continue;
        }
        for (LXPattern pattern : patterns) {
          if (patternLane.engine == pattern.getEngine()
              && patternLane.findEventIndices(pattern) != null) {
            references.add(new ExternalReference("clipPatternEvent", clipPath, null,
                Resolve.canonicalPath(lane)));
          }
        }
      }
    }
  }

  /** {@code component} itself if it is a pattern, plus every pattern nested beneath it. */
  private static List<LXPattern> subtreePatterns(LXComponent component) {
    List<LXPattern> patterns = new ArrayList<>();
    if (component instanceof LXPattern pattern) {
      patterns.add(pattern);
    }
    if (component instanceof LXPatternEngine.Container container) {
      for (LXPattern child : container.getPatternEngine().patterns) {
        patterns.addAll(subtreePatterns(child));
      }
    }
    return patterns;
  }

  /**
   * The channel created by {@link #copyChannel}, the external references the copy does not
   * reproduce, and whether the source's group membership was dropped.
   */
  public record ChannelCopyResult(LXChannel channel, List<ExternalReference> unreplicatedWiring,
      boolean groupMembershipDropped) {}

  /** LXChannel serializes its group by component id under this key (LXChannel.java:497). */
  private static final String KEY_CHANNEL_GROUP = "group";

  /**
   * Copy a whole channel — patterns, effects, clips, and its own channel-level modulation
   * engine — into the mixer at {@code index}.
   *
   * <p>A channel carries far more than a pattern does, because channel-level wiring lives
   * <em>inside</em> a channel's subtree: the modulators and modulations that
   * {@link #copyPattern} has to strand are exactly what a channel copy takes with it,
   * rewired to the copy. Only global modulations, MIDI mappings and snapshot views stay
   * behind, and those are reported in
   * {@link ChannelCopyResult#unreplicatedWiring()}.
   *
   * <p>Two hazards in LX's own behaviour are handled here rather than passed through:
   *
   * <ul>
   * <li><b>Groups cannot be copied.</b> {@code Mixer.AddChannel} always constructs an
   * {@code LXChannel} (LXMixerEngine.java:524), so handing it a serialized {@code LXGroup}
   * yields a plain channel wearing the group's label and none of its members. Rejected.
   * <li><b>Group membership is stripped from the copy.</b> The serialized channel keeps a
   * {@code group} id, and honouring it while inserting anywhere in the mixer breaks LX's
   * invariant that a group's members are contiguous behind it — verified to produce a group
   * reporting 3 members with an outsider wedged between them. The copy therefore lands as a
   * top-level channel; {@code groupMembershipDropped} says so, and callers who want it
   * grouped call {@link #groupChannels} afterward.
   * </ul>
   *
   * @param index 0-based mixer index, or -1 to append
   * @throws Resolve.ResolveException TYPE_MISMATCH if the source is a group or the index is
   *     out of range
   */
  public static ChannelCopyResult copyChannel(LX lx, String sourcePath, int index) {
    LXAbstractChannel source = Resolve.component(lx, sourcePath, LXAbstractChannel.class);
    if (!(source instanceof LXChannel channel)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Cannot copy a group at " + sourcePath
              + " — LX's AddChannel only builds channels, so a group copy would lose every "
              + "member; copy the member channels individually and regroup them");
    }
    List<LXAbstractChannel> channels = lx.engine.mixer.channels;
    int before = channels.size();
    // addChannel throws on index > size inside perform(), which swallows it and wipes the
    // undo stack — reject up front like the other index-taking primitives.
    if (index > before) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Channel index " + index + " out of range [0," + before + "]");
    }
    // Stripping the copy's own group id keeps it out of the source's group, but says
    // nothing about where it lands: an ungrouped channel dropped between a group header
    // and its members breaks the same contiguity invariant from the other side. Nothing is
    // being removed here, so the split test runs against the full channel list.
    if (index >= 0 && insertionSplitsGroup(channels, null, index)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Channel index " + index + " would land inside a group and split it; copy to an "
              + "index outside the group, then use group_channels");
    }
    JsonObject channelObj = LXSerializable.Utils.toObject(channel, true);
    boolean groupMembershipDropped = channelObj.has(KEY_CHANNEL_GROUP);
    channelObj.remove(KEY_CHANNEL_GROUP);
    Commands.perform(lx, new LXCommand.Mixer.AddChannel(channelObj, index));
    if (channels.size() != before + 1) {
      throw new IllegalStateException("AddChannel did not add a channel");
    }
    int resultIndex = (index >= 0 && index <= before) ? index : before;
    if (!(channels.get(resultIndex) instanceof LXChannel copy)) {
      throw new IllegalStateException("AddChannel did not add an LXChannel");
    }
    return new ChannelCopyResult(copy, externalReferences(lx, channel), groupMembershipDropped);
  }

  /**
   * Remove a pattern by path.
   *
   * @throws Resolve.ResolveException NOT_FOUND / TYPE_MISMATCH
   */
  public static void removePattern(LX lx, String path) {
    LXPattern pattern = Resolve.component(lx, path, LXPattern.class);
    // getEngine(), not the deprecated getChannel(): rack-hosted patterns have a
    // PatternRack parent, and the cast would blow up (LXPattern.java:412-418).
    LXPatternEngine engine = pattern.getEngine();
    Commands.perform(lx, new LXCommand.Channel.RemovePattern(engine, pattern));
  }

  /**
   * The result of activating a pattern: {@code active} reports whether it landed
   * immediately. With a transition blend configured, {@code GoPattern} starts a
   * transition — the pattern is pending, not yet active, until it lands.
   */
  public record ActivationResult(LXPattern pattern, boolean active) {}

  /**
   * Activate a pattern (GoPattern — only valid in PLAYLIST mode).
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH when the channel is in BLEND mode.
   */
  public static ActivationResult activatePattern(LX lx, String path) {
    LXPattern pattern = Resolve.component(lx, path, LXPattern.class);
    LXPatternEngine engine = pattern.getEngine();
    if (!engine.isPlaylist()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          engine.component.getCanonicalPath() + " is in BLEND composite mode — control "
              + "visibility there via set_parameter on the pattern's enabled (on/off) and "
              + "compositeLevel (0-1) parameters");
    }
    Commands.perform(lx, new LXCommand.Channel.GoPattern(engine, pattern));
    return new ActivationResult(pattern, pattern.getEngine().getActivePattern() == pattern);
  }

  /** The moved pattern plus every sibling whose canonical path changed as a result. */
  public record PatternMoveResult(LXPattern pattern, List<PathChange> oscChanges) {}

  /** The moved channel/group plus every mixer component whose canonical path changed. */
  public record ChannelMoveResult(LXAbstractChannel channel, List<PathChange> oscChanges) {}

  /**
   * The moved effect, every canonical path that changed, and the wiring the move destroyed
   * — always empty for an in-container reorder, which cannot take an effect out of any
   * engine's reach (see {@link #moveEffect}).
   */
  public record EffectMoveResult(LXEffect effect, List<PathChange> oscChanges,
      List<ExternalReference> droppedWiring) {}

  /**
   * Move a channel or group to an absolute 0-based index in the mixer list. The destination
   * is interpreted after removing the moved channel (or the whole group block). Membership
   * is preserved: grouped channels may only move within their group, and top-level buses may
   * not be inserted into the middle of a group.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the destination is out of range or
   *     would change group membership
   */
  public static ChannelMoveResult moveChannel(LX lx, String path, int toIndex) {
    LXAbstractChannel channel = Resolve.component(lx, path, LXAbstractChannel.class);
    List<LXAbstractChannel> channels = lx.engine.mixer.channels;
    int blockSize = channel instanceof LXGroup movedGroup ? movedGroup.channels.size() + 1 : 1;
    int remainingSize = channels.size() - blockSize;
    if (toIndex < 0 || toIndex > remainingSize) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Channel index " + toIndex + " out of range [0," + remainingSize + "] on "
              + Resolve.canonicalPath(lx.engine.mixer));
    }

    LXGroup currentGroup = channel.getGroup();
    if (currentGroup != null) {
      int firstMemberIndex = currentGroup.getIndex() + 1;
      int afterLastMemberIndex = currentGroup.getIndex() + currentGroup.channels.size();
      if (toIndex < firstMemberIndex || toIndex > afterLastMemberIndex) {
        throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
            "Grouped channel destination " + toIndex + " must stay within ["
                + firstMemberIndex + "," + afterLastMemberIndex + "] for "
                + currentGroup.getCanonicalPath() + "; move_channel does not change membership");
      }
    } else if (insertionSplitsGroup(channels, channel, toIndex)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Channel destination " + toIndex + " would split a group; group membership is "
              + "outside move_channel's scope");
    }

    var before = snapshotPaths(channels);
    LXComposition composition = lx.engine.timeline.getComposition();
    if (composition != null) {
      for (LXClipLane<?> lane : composition.lanes) {
        collectSubtree(lane, before);
      }
    }
    // DropChannel's group implementation expects the old flat-list coordinate and then
    // subtracts the member count after removing the block. Translate our consistent
    // post-removal destination contract back to that coordinate for rightward moves.
    int commandIndex = toIndex;
    if (channel instanceof LXGroup movedGroup && toIndex > channel.getIndex()) {
      commandIndex += movedGroup.channels.size();
    }
    Commands.perform(lx, new LXCommand.Mixer.DropChannel(channel, commandIndex, currentGroup));
    if (channel.getIndex() != toIndex) {
      throw new IllegalStateException("DropChannel placed " + channel.getId() + " at index "
          + channel.getIndex() + " instead of requested index " + toIndex);
    }
    return new ChannelMoveResult(channel, pathChanges(lx, before));
  }

  private static boolean insertionSplitsGroup(List<LXAbstractChannel> channels,
      LXAbstractChannel moving, int toIndex) {
    List<LXAbstractChannel> remaining = new ArrayList<>();
    LXGroup movedGroup = moving instanceof LXGroup group ? group : null;
    for (LXAbstractChannel candidate : channels) {
      if (candidate == moving || (movedGroup != null && candidate.getGroup() == movedGroup)) {
        continue;
      }
      remaining.add(candidate);
    }
    if (toIndex == 0 || toIndex == remaining.size()) {
      return false;
    }
    LXAbstractChannel left = remaining.get(toIndex - 1);
    LXAbstractChannel right = remaining.get(toIndex);
    if (left instanceof LXGroup leftGroup) {
      return right.getGroup() == leftGroup;
    }
    return left.getGroup() != null && left.getGroup() == right.getGroup();
  }

  /**
   * Move a pattern to a new 0-based index within its channel.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if index is out of range
   */
  public static PatternMoveResult movePattern(LX lx, String path, int toIndex) {
    LXPattern pattern = Resolve.component(lx, path, LXPattern.class);
    LXPatternEngine engine = pattern.getEngine();
    int size = engine.patterns.size();
    if (toIndex < 0 || toIndex >= size) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Pattern index " + toIndex + " out of range [0," + (size - 1) + "] on "
              + engine.component.getCanonicalPath());
    }
    var before = snapshotPaths(engine.patterns);
    Commands.perform(lx, new LXCommand.Channel.MovePattern(engine, pattern, toIndex));
    return new PatternMoveResult(pattern, pathChanges(lx, before));
  }

  /**
   * Resolve an effect host: an LXBus (channel or master) or an LXPattern.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for any other path
   */
  private static LXEffect.Container effectContainer(LX lx, String containerPath) {
    LXComponent component = Resolve.component(lx, containerPath);
    if (!(component instanceof LXEffect.Container container)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Not a channel, bus, or pattern at path: " + containerPath
              + " (found " + component.getClass().getSimpleName() + ")");
    }
    return container;
  }

  /**
   * The effect created by {@link #copyEffect}, plus every external reference into the
   * source that the copy does not reproduce.
   */
  public record EffectCopyResult(LXEffect effect, List<ExternalReference> unreplicatedWiring) {}

  /**
   * Copy a configured effect into {@code containerPath} (a channel, the master bus, or a
   * pattern), which may be the source's own container.
   *
   * <p>Same round-trip as {@link #copyPattern}: the effect's parameter values and its own
   * device-local modulation engine travel with the copy; wiring held outside the effect
   * does not, and is returned in {@link EffectCopyResult#unreplicatedWiring()}.
   *
   * <p>Unlike {@link #copyPattern} there is no insertion index — {@code AddEffect.perform}
   * calls {@code addEffect(instance)} with no index and always appends (LXCommand.java:1387).
   * Callers wanting a position follow with {@link #moveEffect}.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the destination is not an effect
   *     container
   */
  public static EffectCopyResult copyEffect(LX lx, String sourcePath, String containerPath) {
    LXEffect source = Resolve.component(lx, sourcePath, LXEffect.class);
    LXEffect.Container container = effectContainer(lx, containerPath);
    List<LXEffect> effects = container.getEffects();
    int before = effects.size();
    JsonObject effectObj = LXSerializable.Utils.toObject(source, true);
    Commands.perform(lx,
        new LXCommand.Channel.AddEffect((LXComponent) container, source.getClass(), effectObj));
    if (effects.size() != before + 1) {
      throw new IllegalStateException("AddEffect did not add an effect to " + containerPath);
    }
    return new EffectCopyResult(effects.get(before), externalReferences(lx, source));
  }

  /**
   * Move an effect to a different container, at {@code index} within it.
   *
   * <p>This is LX's own {@code Channel.RelocateEffect} (LXCommand.java:1530-1615) — a real
   * move, not a copy: the effect keeps its identity, and its MIDI mappings, snapshot views
   * and clip lanes are retargeted to the new path.
   *
   * <p>Modulations and triggers are retargeted only while they stay in scope.
   * {@code LXParameterModulation.move} returns null — dropping the wiring — when the moved
   * component is no longer a descendant of the engine's parent
   * (LXParameterModulation.java:265-269). So moving an effect between two containers on the
   * same channel preserves that channel's wiring, while moving it to another channel
   * destroys it. Because the test is a pure scope check, the casualties are known before
   * the command runs, and they are returned in
   * {@link EffectRelocateResult#droppedWiring()} instead of vanishing silently. Undo
   * restores them.
   *
   * <p>Custom remote controls referencing the effect are also dropped and restored only by
   * undo; LX flags that as an open TODO (LXCommand.java:1607-1611) and does not report it.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the destination is not an effect
   *     container, is the effect's own current container, or the index is out of range
   */
  private static EffectMoveResult relocateEffect(LX lx, String path, String containerPath,
      int index) {
    LXEffect effect = Resolve.component(lx, path, LXEffect.class);
    // A relocation removes the effect from its source first, and LX refuses to remove a
    // locked effect inside perform() — which swallows the failure and wipes the undo
    // stack. Same precheck removeEffect makes, for the same reason.
    if (effect.locked.isOn()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Effect " + path + " is locked and cannot be moved to another container");
    }
    LXEffect.Container target = effectContainer(lx, containerPath);
    if (target == effect.getContainer()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Effect " + path + " is already in " + containerPath
              + " — use move_effect without containerPath to reorder within a container");
    }
    // The effect is removed from its source before being loaded into the target, so the
    // target's list grows by one; an out-of-range index would throw inside perform(), which
    // swallows it and wipes the undo stack.
    int size = target.getEffects().size();
    if (index < 0 || index > size) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Effect index " + index + " out of range [0," + size + "] on " + containerPath);
    }
    List<ExternalReference> dropped = outOfScopeWiring(effect, (LXComponent) target);
    // RelocateEffect reindexes exactly two effect lists: the one it leaves and the one it
    // joins. snapshotPaths descends each effect's own subtree (its device-local modulators
    // and wirings shift with it), so this is both narrower and deeper than walking the
    // whole mixer.
    List<LXEffect> affected = new ArrayList<>(effect.getContainer().getEffects());
    affected.addAll(target.getEffects());
    var before = snapshotPaths(affected);
    Commands.perform(lx, new LXCommand.Channel.RelocateEffect(effect, target, index));
    if (target.getEffects().size() != size + 1) {
      throw new IllegalStateException("RelocateEffect did not move an effect to " + containerPath);
    }
    // RelocateEffect removes the effect and loads a fresh instance into the target
    // (LXCommand.java:1596), so the resolved reference is now detached. The new instance
    // reclaims the original's id — freed by the removal — which is why oscChanges can track
    // it across the move, but the object is not the same one.
    return new EffectMoveResult(
        target.getEffects().get(index), pathChanges(lx, before), dropped);
  }

  /**
   * The modulations and triggers referencing {@code component} that a move to
   * {@code destination} would destroy: those in an engine whose parent will no longer
   * contain the component. Mirrors the scope test in {@code LXParameterModulation.move}.
   */
  private static List<ExternalReference> outOfScopeWiring(LXComponent component,
      LXComponent destination) {
    List<ExternalReference> dropped = new ArrayList<>();
    // MIDI mappings, snapshot views and clip lanes are retargeted unconditionally, so among
    // wirings only modulations and triggers can be casualties — exactly the ones whose
    // engine will no longer contain the component. Same test LXParameterModulation.move
    // applies.
    modulationReferences(component, engine -> !destination.isDescendant(engine.getParent()),
        dropped);
    // Remote controls are different: RelocateEffect restores every other reference kind but
    // deliberately skips these (LXCommand.java:1607-1611 marks it an open TODO), so an
    // ancestor device pointing at the component loses that control regardless of scope.
    remoteControlReferences(component, dropped);
    return dropped;
  }

  /**
   * Ancestor devices whose custom remote controls address {@code component}. Mirrors LX's
   * own {@code RemoveComponent} walk, which stops at the enclosing bus because remote
   * controls only exist on devices.
   */
  private static void remoteControlReferences(LXComponent component,
      List<ExternalReference> references) {
    for (LXComponent parent = component.getParent();
        parent != null && !(parent instanceof LXBus);
        parent = parent.getParent()) {
      if (!(parent instanceof LXDeviceComponent device)) {
        continue;
      }
      LXListenableNormalizedParameter[] controls = device.getCustomRemoteControls();
      if (controls == null) {
        continue;
      }
      for (LXListenableNormalizedParameter control : controls) {
        if (control != null && control.isDescendant(component)) {
          references.add(new ExternalReference("remoteControl",
              Resolve.canonicalPath(device), null, Resolve.canonicalPath(control)));
        }
      }
    }
  }



  /**
   * Add an effect of {@code effectClass} to a container. The container may be an
   * LXBus (channel or master) or an LXPattern; any other path is a TYPE_MISMATCH.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for non-container paths
   */
  public static LXEffect addEffect(LX lx, String containerPath,
      Class<? extends LXEffect> effectClass) {
    LXEffect.Container container = effectContainer(lx, containerPath);
    List<LXEffect> effects = container.getEffects();
    int before = effects.size();
    Commands.perform(lx, new LXCommand.Channel.AddEffect((LXComponent) container, effectClass));
    if (effects.size() != before + 1) {
      throw new IllegalStateException("AddEffect did not add an effect to " + containerPath);
    }
    return effects.get(before);
  }

  /**
   * Remove an effect by path. Pre-checks the locked flag before constructing the
   * command, so the undo stack is never touched when rejection is guaranteed.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the effect is locked.
   */
  public static void removeEffect(LX lx, String path) {
    LXEffect effect = Resolve.component(lx, path, LXEffect.class);
    if (effect.locked.isOn()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Effect " + path + " is locked and cannot be removed");
    }
    LXComponent parent = (LXComponent) effect.getContainer();
    Commands.perform(lx, new LXCommand.Channel.RemoveEffect(parent, effect));
  }

  /**
   * Move an effect to a new 0-based index within its container (bus or pattern).
   *
   * @throws Resolve.ResolveException if index is out of range
   */
  /**
   * Move an effect: to {@code index} within its current container when
   * {@code containerPath} is null, or into a different container when it is given.
   *
   * <p>One entry point because the two cases differ in what they can destroy, and that
   * difference is LX's, not the caller's: a reorder keeps the effect inside every engine
   * that reaches it, while a cross-container move can take it out of scope and drop the
   * wiring outright. Both report through {@link EffectMoveResult#droppedWiring()}, so a
   * caller never has to know which path it took.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if the destination is not an effect
   *     container, is the effect's own current container, or the index is out of range
   */
  public static EffectMoveResult moveEffect(LX lx, String path, String containerPath,
      int index) {
    return (containerPath == null)
        ? reorderEffect(lx, path, index)
        : relocateEffect(lx, path, containerPath, index);
  }

  private static EffectMoveResult reorderEffect(LX lx, String path, int toIndex) {
    LXEffect effect = Resolve.component(lx, path, LXEffect.class);
    LXEffect.Container container = effect.getContainer();
    List<LXEffect> effects = container.getEffects();
    int size = effects.size();
    if (toIndex < 0 || toIndex >= size) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Effect index " + toIndex + " out of range [0," + (size - 1) + "] on "
              + ((LXComponent) container).getCanonicalPath());
    }
    LXComponent parent = (LXComponent) container;
    var before = snapshotPaths(effects);
    Commands.perform(lx, new LXCommand.Channel.MoveEffect(parent, effect, toIndex));
    // An in-container reorder never moves the effect out of an engine's reach, so no
    // wiring can be dropped — the empty list is a fact about LX, stated here rather than
    // fabricated by each caller.
    return new EffectMoveResult(effect, pathChanges(lx, before), List.of());
  }

  // ── Snapshot builders ────────────────────────────────────────────────────────

  /** Snapshot a single channel or group. Exposed for get_channel's O(1) drill-down. */
  public static ChannelInfo describe(LXAbstractChannel channel) {
    List<PatternInfo> patterns = List.of();
    PatternMode patternMode = null;
    PatternEngineControls patternEngineControls = null;
    int containerPatternCount = 0;
    boolean anyLocalModulation = false;
    if (channel instanceof LXChannel c) {
      boolean blend = c.isComposite();
      patternMode = blend ? PatternMode.BLEND : PatternMode.PLAYLIST;
      patterns = new ArrayList<>();
      LXPattern active = c.getActivePattern();
      for (LXPattern pattern : c.patterns) {
        boolean patternEnabled = pattern.enabled.isOn();
        double compositeLevel = pattern.compositeLevel.getValue();
        boolean isActive = pattern == active;
        boolean contributing = blend ? (patternEnabled && compositeLevel > 0) : isActive;
        int nestedPatternCount = (pattern instanceof LXPatternEngine.Container container)
            ? container.getPatternEngine().patterns.size()
            : 0;
        if (nestedPatternCount > 0) {
          containerPatternCount++;
        }
        boolean patternHasLocalModulation = hasLocalModulation(pattern);
        anyLocalModulation |= patternHasLocalModulation;
        List<EffectInfo> patternEffects = effects(pattern.getEffects());
        for (EffectInfo effect : patternEffects) {
          anyLocalModulation |= effect.hasLocalModulation();
        }
        patterns.add(new PatternInfo(
            Resolve.canonicalPathOrNull(pattern),
            pattern.getId(),
            pattern.getLabel(),
            pattern.getClass().getName(),
            isActive,
            patternEnabled,
            compositeLevel,
            contributing,
            nestedPatternCount,
            patternHasLocalModulation,
            Views.viewRef(pattern),
            patternEffects));
      }
      patternEngineControls = patternEngineControls(c.getPatternEngine());
    }
    List<EffectInfo> channelEffects = effects(channel.getEffects());
    for (EffectInfo effect : channelEffects) {
      anyLocalModulation |= effect.hasLocalModulation();
    }
    LXGroup group = channel.getGroup();
    return new ChannelInfo(
        Resolve.canonicalPathOrNull(channel),
        channel.getId(),
        channel.getLabel(),
        channel.getIndex(),
        (channel instanceof LXGroup) ? BusType.GROUP : BusType.CHANNEL,
        channel.enabled.isOn(),
        channel.fader.getValue(),
        (group == null) ? null : Resolve.canonicalPathOrNull(group),
        patternMode,
        patterns,
        channelEffects,
        channelControls(channel, patternEngineControls),
        containerPatternCount,
        anyLocalModulation,
        Views.viewRef(channel));
  }

  /** Snapshot the master bus. Exposed for get_channel's O(1) drill-down. */
  public static MasterInfo describeMaster(LXBus master) {
    List<EffectInfo> masterEffects = effects(master.getEffects());
    boolean anyLocalModulation = false;
    for (EffectInfo effect : masterEffects) {
      anyLocalModulation |= effect.hasLocalModulation();
    }
    return new MasterInfo(
        Resolve.canonicalPathOrNull(master),
        master.getId(),
        master.getLabel(),
        master.fader.getValue(),
        masterEffects,
        anyLocalModulation);
  }

  // ── Private helpers ──────────────────────────────────────────────────────────

  private static ChannelControls channelControls(LXAbstractChannel channel,
      PatternEngineControls patternEngineControls) {
    return new ChannelControls(
        new EnumField(channel.crossfadeGroup.getOption(), null, Resolve.canonicalPathOrNull(channel.crossfadeGroup)),
        new EnumField(channel.blendMode.getObject().getLabel(), null, Resolve.canonicalPathOrNull(channel.blendMode)),
        new Field<>(channel.autoMute.isOn(), Resolve.canonicalPathOrNull(channel.autoMute)),
        // isAutoMuted is a read-only derived flag that isn't path-registered
        // (LXAbstractChannel.java:113); canonicalPathOrNull detects that and reports the
        // value only, with no path.
        new Field<>(channel.isAutoMuted.isOn(), Resolve.canonicalPathOrNull(channel.isAutoMuted)),
        new Field<>(channel.cueActive.isOn(), Resolve.canonicalPathOrNull(channel.cueActive)),
        new Field<>(channel.auxActive.isOn(), Resolve.canonicalPathOrNull(channel.auxActive)),
        patternEngineControls);
  }

  private static PatternEngineControls patternEngineControls(LXPatternEngine engine) {
    return new PatternEngineControls(
        new Field<>(engine.autoCycleEnabled.isOn(), Resolve.canonicalPathOrNull(engine.autoCycleEnabled)),
        new EnumField(engine.autoCycleMode.getOption(), null, Resolve.canonicalPathOrNull(engine.autoCycleMode)),
        new Field<>(engine.autoCycleTimeSecs.getValue(), Resolve.canonicalPathOrNull(engine.autoCycleTimeSecs)),
        new Field<>(engine.transitionEnabled.isOn(), Resolve.canonicalPathOrNull(engine.transitionEnabled)),
        new Field<>(engine.transitionTimeSecs.getValue(), Resolve.canonicalPathOrNull(engine.transitionTimeSecs)),
        new EnumField(engine.transitionBlendMode.getObject().getLabel(), null,
            Resolve.canonicalPathOrNull(engine.transitionBlendMode)));
  }

  /**
   * Id -> canonical path for every sibling and its full descendant subtree (owned
   * effects, and for a {@link heronarts.lx.mixer.LXPatternEngine.Container} pattern
   * such as PatternRack, its nested patterns and their effects, to arbitrary depth),
   * taken before a move mutates the list. A sibling's index shift cascades to every
   * component that sibling transitively owns, since canonical paths are built by
   * walking the whole parent chain (LXPath) — this walk has to mirror that.
   */
  private static LinkedHashMap<Integer, String> snapshotPaths(List<? extends LXComponent> siblings) {
    LinkedHashMap<Integer, String> snapshot = new LinkedHashMap<>();
    for (LXComponent sibling : siblings) {
      collectSubtree(sibling, snapshot);
    }
    return snapshot;
  }

  // No explicit depth guard: LXComponent.setParent enforces a single-parent invariant
  // (throws if a parent is already set, or if parent == this), so the component graph
  // is a tree and this recursion always terminates without revisiting a node.
  private static void collectSubtree(LXComponent component, Map<Integer, String> snapshot) {
    String path = Resolve.canonicalPathOrNull(component);
    if (path != null) {
      snapshot.put(component.getId(), path);
    }
    if (component instanceof LXBus bus) {
      for (LXClip clip : bus.clips) {
        // Sparse by design — an empty grid clip slot is a null entry (see LXBus.addClip).
        if (clip != null) {
          collectSubtree(clip, snapshot);
        }
      }
    }
    if (component instanceof LXClip clip) {
      // Lanes are LXComponents with resolvable canonical paths (synthesized by Resolve
      // because upstream lane getPath() implementations are inconsistent). Clip events
      // are serializable value objects, not LXComponents, and have no canonical address.
      for (LXClipLane<?> lane : clip.lanes) {
        collectSubtree(lane, snapshot);
      }
    }
    if (component instanceof LXEffect.Container container) {
      for (LXEffect effect : container.getEffects()) {
        collectSubtree(effect, snapshot);
      }
    }
    if (component instanceof LXPatternEngine.Container container) {
      for (LXPattern pattern : container.getPatternEngine().patterns) {
        collectSubtree(pattern, snapshot);
      }
    }
    // Patterns/effects and mixer buses all implement LXModulationContainer. Their
    // modulators, compound modulations, and triggers are addressed by canonical paths
    // prefixed with the owner, so they shift right alongside it.
    if (component instanceof LXModulationContainer container) {
      LXModulationEngine modulation = container.getModulationEngine();
      for (LXModulator modulator : modulation.modulators) {
        collectSubtree(modulator, snapshot);
      }
      for (LXCompoundModulation wiring : modulation.modulations) {
        collectSubtree(wiring, snapshot);
      }
      for (LXTriggerModulation trigger : modulation.triggers) {
        collectSubtree(trigger, snapshot);
      }
    }
  }

  /**
   * Diff a pre-move id->path snapshot against each id's current (post-move) canonical
   * path. Re-reads every path live via {@code lx.getComponent(id)} rather than trusting
   * index arithmetic, so this stays correct even if LX's reindexing behavior changes.
   * Ids that no longer resolve (component removed since the snapshot) are skipped
   * rather than treated as a change.
   */
  private static List<PathChange> pathChanges(LX lx, Map<Integer, String> before) {
    List<PathChange> changes = new ArrayList<>();
    for (Map.Entry<Integer, String> entry : before.entrySet()) {
      LXComponent component = lx.getComponent(entry.getKey());
      if (component == null) {
        // A move doesn't remove anything on its own, so this shouldn't fire in
        // practice; it's a defensive skip against a component vanishing (e.g. a
        // concurrent removal) between the snapshot and this diff. oscChanges only
        // reports paths that changed, not components that disappeared.
        continue;
      }
      String after = Resolve.canonicalPathOrNull(component);
      if (after != null && !after.equals(entry.getValue())) {
        changes.add(new PathChange(entry.getKey(), entry.getValue(), after));
      }
    }
    return changes;
  }

  private static List<EffectInfo> effects(List<LXEffect> effects) {
    List<EffectInfo> result = new ArrayList<>();
    for (LXEffect effect : effects) {
      result.add(new EffectInfo(
          Resolve.canonicalPathOrNull(effect),
          effect.getId(),
          effect.getLabel(),
          effect.getClass().getName(),
          effect.enabled.isOn(),
          hasLocalModulation(effect),
          Views.viewRef(effect)));
    }
    return result;
  }

  /**
   * Every {@link LXDeviceComponent} (pattern or effect) owns its own {@code modulation}
   * engine, separate from the global one — invisible to {@code list_channels} and to a
   * scope-less {@code list_modulations} call alike. True if that engine hosts any
   * modulator, continuous modulation, or trigger (see issue #117).
   */
  private static boolean hasLocalModulation(LXDeviceComponent device) {
    LXModulationEngine modulation = device.modulation;
    return !modulation.modulators.isEmpty()
        || !modulation.modulations.isEmpty()
        || !modulation.triggers.isEmpty();
  }
}
