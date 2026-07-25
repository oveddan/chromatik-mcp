package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulation.LXTriggerModulation;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.pattern.LXPattern;

/**
 * Read-only snapshots and mutations of the mixer. Snapshot records are immutable copies
 * assembled on the engine thread, so tool handlers never hold live LX objects off-thread.
 * Mutation primitives are also called on the engine thread and route through LXCommand.
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
      boolean hasLocalModulation, List<EffectInfo> effects) {}

  /** {@code hasLocalModulation}: see {@link #hasLocalModulation(LXDeviceComponent)}. */
  public record EffectInfo(String path, int id, String label, String className, boolean enabled,
      boolean hasLocalModulation) {}

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
   */
  public record ChannelInfo(String path, int id, String label, int index, BusType type,
      boolean enabled, double fader, String groupPath, PatternMode patternMode,
      List<PatternInfo> patterns, List<EffectInfo> effects, ChannelControls controls,
      int containerPatternCount, boolean anyLocalModulation) {}

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

  /**
   * Add a pattern of {@code patternClass} to the channel at {@code channelPath}.
   * When this is the first pattern added via MCP (the channel was empty), the pattern
   * auto-activates because LX's engine treats the first pattern as active.
   *
   * @param index 0-based insertion index, or -1 to append
   */
  public static LXPattern addPattern(LX lx, String channelPath,
      Class<? extends LXPattern> patternClass, int index) {
    LXChannel channel = Resolve.component(lx, channelPath, LXChannel.class);
    LXPatternEngine engine = channel.getPatternEngine();
    int before = engine.patterns.size();
    // LX's addPattern throws on index > size INSIDE perform(), which swallows it and
    // wipes the undo stack — reject up front like the other index-taking primitives.
    if (index > before) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Pattern index " + index + " out of range [0," + before + "] on " + channelPath);
    }
    Commands.perform(lx, new LXCommand.Channel.AddPattern(engine, patternClass, index));
    if (engine.patterns.size() != before + 1) {
      throw new IllegalStateException("AddPattern did not add a pattern to " + channelPath);
    }
    // Inserted patterns land at their target index; new appended patterns are last.
    int resultIndex = (index >= 0 && index <= before) ? index : before;
    return engine.patterns.get(resultIndex);
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

  /**
   * A single component's canonical-path change caused by a move — either the moved
   * component itself or a sibling whose 1-based index (and therefore path) shifted.
   * Keyed by component id, never by list position, since position is exactly what
   * a move changes.
   */
  public record PathChange(int componentId, String before, String after) {}

  /** The moved pattern plus every sibling whose canonical path changed as a result. */
  public record PatternMoveResult(LXPattern pattern, List<PathChange> oscChanges) {}

  /** The moved effect plus every sibling whose canonical path changed as a result. */
  public record EffectMoveResult(LXEffect effect, List<PathChange> oscChanges) {}

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
   * Add an effect of {@code effectClass} to a container. The container may be an
   * LXBus (channel or master) or an LXPattern; any other path is a TYPE_MISMATCH.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for non-container paths
   */
  public static LXEffect addEffect(LX lx, String containerPath,
      Class<? extends LXEffect> effectClass) {
    LXComponent component = Resolve.component(lx, containerPath);
    if (!(component instanceof LXEffect.Container container)) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Not a channel, bus, or pattern at path: " + containerPath
              + " (found " + component.getClass().getSimpleName() + ")");
    }
    List<LXEffect> effects = container.getEffects();
    int before = effects.size();
    Commands.perform(lx, new LXCommand.Channel.AddEffect(component, effectClass));
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
  public static EffectMoveResult moveEffect(LX lx, String path, int toIndex) {
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
    return new EffectMoveResult(effect, pathChanges(lx, before));
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
        anyLocalModulation);
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
    snapshot.put(component.getId(), component.getCanonicalPath());
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
    // Every LXPattern/LXEffect is an LXDeviceComponent and unconditionally owns a
    // modulation engine (LXDeviceComponent.java:87,157); its modulators, compound
    // modulations, and triggers are addressed by canonical paths prefixed with this
    // component's own path segment, so they shift right alongside it.
    if (component instanceof LXDeviceComponent device) {
      for (LXModulator modulator : device.modulation.modulators) {
        collectSubtree(modulator, snapshot);
      }
      for (LXCompoundModulation modulation : device.modulation.modulations) {
        collectSubtree(modulation, snapshot);
      }
      for (LXTriggerModulation trigger : device.modulation.triggers) {
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
      String after = component.getCanonicalPath();
      if (!after.equals(entry.getValue())) {
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
          hasLocalModulation(effect)));
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
