package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.pattern.LXPattern;

/**
 * Read-only snapshots and mutations of the mixer. Snapshot records are immutable copies
 * assembled on the engine thread, so tool handlers never hold live LX objects off-thread.
 * Mutation primitives are also called on the engine thread and route through LXCommand.
 */
public final class Channels {

  public enum BusType { CHANNEL, GROUP }

  public record PatternInfo(String path, int id, String label, String className, boolean active) {}

  public record EffectInfo(String path, int id, String label, String className, boolean enabled) {}

  /**
   * {@code groupPath} is the canonical path of the enclosing group, or null at top level
   * — the mixer's channel list is flat, with group members as siblings of their group.
   * {@code compositeMode} channels render multiple patterns at once, so the single
   * {@code active} flag on patterns is only meaningful when it is false.
   */
  public record ChannelInfo(String path, int id, String label, int index, BusType type,
      boolean enabled, double fader, String groupPath, boolean compositeMode,
      List<PatternInfo> patterns, List<EffectInfo> effects) {}

  public record MasterInfo(String path, int id, String label, double fader, List<EffectInfo> effects) {}

  public record MixerInfo(List<ChannelInfo> channels, MasterInfo master) {}

  private Channels() {}

  /** Call on the engine thread; the returned records are safe to read anywhere. */
  public static MixerInfo list(LX lx) {
    List<ChannelInfo> channels = new ArrayList<>();
    for (LXAbstractChannel channel : lx.engine.mixer.channels) {
      channels.add(describe(channel));
    }
    LXBus master = lx.engine.mixer.masterBus;
    return new MixerInfo(channels, new MasterInfo(
        master.getCanonicalPath(),
        master.getId(),
        master.getLabel(),
        master.fader.getValue(),
        effects(master)));
  }

  // ── Channel mutations ────────────────────────────────────────────────────────

  /**
   * Resolve a pattern class name against the LX registry.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for an unregistered name.
   */
  public static Class<? extends LXPattern> resolvePatternClass(LX lx, String className) {
    for (Class<? extends LXPattern> clazz : lx.registry.patterns) {
      if (clazz.getName().equals(className)) {
        return clazz;
      }
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        "Unknown pattern type: " + className + " (see list_available_patterns)");
  }

  /**
   * Resolve an effect class name against the LX registry.
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH for an unregistered name.
   */
  public static Class<? extends LXEffect> resolveEffectClass(LX lx, String className) {
    for (Class<? extends LXEffect> clazz : lx.registry.effects) {
      if (clazz.getName().equals(className)) {
        return clazz;
      }
    }
    throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
        "Unknown effect type: " + className + " (see list_available_effects)");
  }

  /**
   * Add a channel to the mixer, optionally pre-populated with a pattern.
   *
   * @param patternClass optional first pattern class; null leaves the channel empty
   */
  public static LXChannel addChannel(LX lx, Class<? extends LXPattern> patternClass) {
    List<LXAbstractChannel> before = new ArrayList<>(lx.engine.mixer.channels);
    LXCommand.Mixer.AddChannel cmd = (patternClass != null)
        ? new LXCommand.Mixer.AddChannel(patternClass)
        : new LXCommand.Mixer.AddChannel();
    Commands.perform(lx, cmd);
    // Find the newly added channel — it may not be at the end when groups shift indices.
    for (LXAbstractChannel ch : lx.engine.mixer.channels) {
      if (!before.contains(ch) && ch instanceof LXChannel channel) {
        return channel;
      }
    }
    throw new IllegalStateException("AddChannel did not add a new LXChannel");
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
   * Activate a pattern (GoPattern — only valid in PLAYLIST mode).
   *
   * @throws Resolve.ResolveException TYPE_MISMATCH when the channel is in BLEND mode.
   */
  public static LXPattern activatePattern(LX lx, String path) {
    LXPattern pattern = Resolve.component(lx, path, LXPattern.class);
    LXPatternEngine engine = pattern.getEngine();
    if (!engine.isPlaylist()) {
      throw new Resolve.ResolveException(Resolve.Failure.TYPE_MISMATCH,
          engine.component.getCanonicalPath() + " is in BLEND composite mode — activate "
              + "patterns there via set_parameter on the pattern's enabled parameter");
    }
    Commands.perform(lx, new LXCommand.Channel.GoPattern(engine, pattern));
    return pattern;
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

  // ── Private helpers ──────────────────────────────────────────────────────────

  private static ChannelInfo describe(LXAbstractChannel channel) {
    List<PatternInfo> patterns = List.of();
    boolean compositeMode = false;
    if (channel instanceof LXChannel c) {
      compositeMode = c.isComposite();
      patterns = new ArrayList<>();
      LXPattern active = c.getActivePattern();
      for (LXPattern pattern : c.patterns) {
        patterns.add(new PatternInfo(
            pattern.getCanonicalPath(),
            pattern.getId(),
            pattern.getLabel(),
            pattern.getClass().getName(),
            pattern == active));
      }
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
        compositeMode,
        patterns,
        effects(channel));
  }

  private static List<EffectInfo> effects(LXBus bus) {
    List<EffectInfo> effects = new ArrayList<>();
    for (LXEffect effect : bus.effects) {
      effects.add(new EffectInfo(
          effect.getCanonicalPath(),
          effect.getId(),
          effect.getLabel(),
          effect.getClass().getName(),
          effect.enabled.isOn()));
    }
    return effects;
  }
}
