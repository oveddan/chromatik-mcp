package lxmcp.domain;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.pattern.LXPattern;

/**
 * Read-only snapshots of the mixer. Snapshot records are immutable copies assembled on
 * the engine thread, so tool handlers never hold live LX objects off-thread.
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
