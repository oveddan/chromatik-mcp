package chromatikmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.mixer.LXPatternEngine;
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
   * mode it is {@code enabled && compositeLevel > 0}.
   */
  public record PatternInfo(String path, int id, String label, String className, boolean active,
      boolean enabled, double compositeLevel, boolean contributing, List<EffectInfo> effects) {}

  public record EffectInfo(String path, int id, String label, String className, boolean enabled) {}

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
   */
  public record ChannelInfo(String path, int id, String label, int index, BusType type,
      boolean enabled, double fader, String groupPath, PatternMode patternMode,
      List<PatternInfo> patterns, List<EffectInfo> effects, ChannelControls controls) {}

  public record MasterInfo(String path, int id, String label, double fader, List<EffectInfo> effects) {}

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
        new Field<>(mixer.crossfader.getValue(), mixer.crossfader.getCanonicalPath()),
        new EnumField(mixer.crossfaderBlendMode.getObject().getLabel(),
            objectOptions(mixer.crossfaderBlendMode), mixer.crossfaderBlendMode.getCanonicalPath()),
        new Field<>(mixer.cueA.isOn(), mixer.cueA.getCanonicalPath()),
        new Field<>(mixer.cueB.isOn(), mixer.cueB.getCanonicalPath()),
        new Field<>(mixer.auxA.isOn(), mixer.auxA.getCanonicalPath()),
        new Field<>(mixer.auxB.isOn(), mixer.auxB.getCanonicalPath()),
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
   * Move a pattern to a new 0-based index within its channel.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH if index is out of range
   */
  public static LXPattern movePattern(LX lx, String path, int toIndex) {
    LXPattern pattern = Resolve.component(lx, path, LXPattern.class);
    LXPatternEngine engine = pattern.getEngine();
    int size = engine.patterns.size();
    if (toIndex < 0 || toIndex >= size) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          "Pattern index " + toIndex + " out of range [0," + (size - 1) + "] on "
              + engine.component.getCanonicalPath());
    }
    Commands.perform(lx, new LXCommand.Channel.MovePattern(engine, pattern, toIndex));
    return pattern;
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
  public static LXEffect moveEffect(LX lx, String path, int toIndex) {
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
    Commands.perform(lx, new LXCommand.Channel.MoveEffect(parent, effect, toIndex));
    return effect;
  }

  // ── Snapshot builders ────────────────────────────────────────────────────────

  /** Snapshot a single channel or group. Exposed for get_channel's O(1) drill-down. */
  public static ChannelInfo describe(LXAbstractChannel channel) {
    List<PatternInfo> patterns = List.of();
    PatternMode patternMode = null;
    PatternEngineControls patternEngineControls = null;
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
        patterns.add(new PatternInfo(
            pattern.getCanonicalPath(),
            pattern.getId(),
            pattern.getLabel(),
            pattern.getClass().getName(),
            isActive,
            patternEnabled,
            compositeLevel,
            contributing,
            effects(pattern.getEffects())));
      }
      patternEngineControls = patternEngineControls(c.getPatternEngine());
    }
    LXGroup group = channel.getGroup();
    return new ChannelInfo(
        channel.getCanonicalPath(),
        channel.getId(),
        channel.getLabel(),
        channel.getIndex(),
        (channel instanceof LXGroup) ? BusType.GROUP : BusType.CHANNEL,
        channel.enabled.isOn(),
        channel.fader.getValue(),
        (group == null) ? null : group.getCanonicalPath(),
        patternMode,
        patterns,
        effects(channel.getEffects()),
        channelControls(channel, patternEngineControls));
  }

  /** Snapshot the master bus. Exposed for get_channel's O(1) drill-down. */
  public static MasterInfo describeMaster(LXBus master) {
    return new MasterInfo(
        master.getCanonicalPath(),
        master.getId(),
        master.getLabel(),
        master.fader.getValue(),
        effects(master.getEffects()));
  }

  // ── Private helpers ──────────────────────────────────────────────────────────

  private static ChannelControls channelControls(LXAbstractChannel channel,
      PatternEngineControls patternEngineControls) {
    return new ChannelControls(
        new EnumField(channel.crossfadeGroup.getOption(), null, channel.crossfadeGroup.getCanonicalPath()),
        new EnumField(channel.blendMode.getObject().getLabel(), null, channel.blendMode.getCanonicalPath()),
        new Field<>(channel.autoMute.isOn(), channel.autoMute.getCanonicalPath()),
        // isAutoMuted is a read-only derived flag that isn't path-registered
        // (LXAbstractChannel.java:113) — getCanonicalPath() would yield "/null", so
        // report the value only, with no path.
        new Field<>(channel.isAutoMuted.isOn(), null),
        new Field<>(channel.cueActive.isOn(), channel.cueActive.getCanonicalPath()),
        new Field<>(channel.auxActive.isOn(), channel.auxActive.getCanonicalPath()),
        patternEngineControls);
  }

  private static PatternEngineControls patternEngineControls(LXPatternEngine engine) {
    return new PatternEngineControls(
        new Field<>(engine.autoCycleEnabled.isOn(), engine.autoCycleEnabled.getCanonicalPath()),
        new EnumField(engine.autoCycleMode.getOption(), null, engine.autoCycleMode.getCanonicalPath()),
        new Field<>(engine.autoCycleTimeSecs.getValue(), engine.autoCycleTimeSecs.getCanonicalPath()),
        new Field<>(engine.transitionEnabled.isOn(), engine.transitionEnabled.getCanonicalPath()),
        new Field<>(engine.transitionTimeSecs.getValue(), engine.transitionTimeSecs.getCanonicalPath()),
        new EnumField(engine.transitionBlendMode.getObject().getLabel(), null,
            engine.transitionBlendMode.getCanonicalPath()));
  }

  private static List<EffectInfo> effects(List<LXEffect> effects) {
    List<EffectInfo> result = new ArrayList<>();
    for (LXEffect effect : effects) {
      result.add(new EffectInfo(
          effect.getCanonicalPath(),
          effect.getId(),
          effect.getLabel(),
          effect.getClass().getName(),
          effect.enabled.isOn()));
    }
    return result;
  }
}
