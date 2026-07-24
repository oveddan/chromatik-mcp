package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Channels;

public final class ListChannels implements LxTool {

  @Override
  public String name() {
    return "list_channels";
  }

  @Override
  public String description() {
    return "List the mixer's channels with their patterns and effects, plus the master bus. "
        + "Defaults to 'detail: summary' — a compact per-channel shape (path, id, label, "
        + "index, type, enabled, fader, patternMode, activePattern, patternCount, "
        + "effectCount) that is the right choice for surveying a project; a real project "
        + "can carry dozens of channels and hundreds of patterns/effects, and the full shape "
        + "blows past client response limits. Pass 'detail: full' for today's complete shape "
        + "(controls block, full patterns array with per-pattern effects, effects array). "
        + "Every entry carries its canonical LX path for use with other tools. "
        + "Channels have two pattern modes ('patternMode'): 'playlist' plays one pattern at a "
        + "time — the one with active=true; 'blend' composites all patterns simultaneously — a "
        + "pattern shows iff enabled=true AND compositeLevel > 0 ('active' is not meaningful in "
        + "blend mode). In playlist mode 'enabled' only affects auto-cycle eligibility — it does "
        + "not hide the active pattern. The per-pattern 'contributing' field (full detail only) "
        + "applies the correct rule for the channel's mode. A contributing pattern is still "
        + "invisible if its channel is disabled or its fader is 0, or if engine output is off "
        + "(see get_project_info). "
        + "Patterns can host their own effect chains — each pattern entry carries its own "
        + "effects list (e.g. a Gradient Mask living inside a pattern rather than on the "
        + "channel; full detail only). "
        + "The top-level 'mixer' object is the crossfader performance surface: 'crossfader' "
        + "runs 0 (full A) to 1 (full B) — only channels whose 'controls.crossfadeGroup' is "
        + "'A' or 'B' (not 'BYPASS') are affected by it, blended via 'crossfaderBlendMode'. "
        + "'cueA'/'cueB'/'auxA'/'auxB' toggle the crossfade-group preview buses (full detail "
        + "only): cue is the primary preview output, aux is a secondary/independent preview "
        + "output — neither affects the main program output. Per-channel and per-pattern-engine "
        + "blend-mode option lists are identical across channels, so they are reported once at "
        + "'mixer.blendModeOptions' / 'mixer.transitionBlendModeOptions' (full detail only) "
        + "rather than repeated on every channel. Each channel's 'controls' block (full detail "
        + "only) carries its crossfade-group assignment, blend mode, auto-mute state, and "
        + "cue/aux preview toggles; 'controls.patternEngine' (playlist/blend channels only — "
        + "absent on groups) carries auto-cycle and pattern-transition settings, with "
        + "'set_parameter' as the mutation path for any of these fields via the accompanying "
        + "canonical 'path'.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("detail", Schemas.enumString(
        "'summary' (default) for a compact survey-friendly shape, or 'full' for today's "
            + "complete per-channel payload (controls, full patterns/effects).",
        List.of("summary", "full")));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object detailArg = args.get("detail");
    if (detailArg != null && !(detailArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "detail must be a string");
    }
    String detail = (String) detailArg;
    if (detail != null && !detail.equals("summary") && !detail.equals("full")) {
      return Result.error(Result.INVALID_ARGUMENT, "detail must be 'summary' or 'full'");
    }

    boolean full = detail != null && detail.equals("full");

    Channels.MixerInfo mixer = Channels.list(lx);

    List<Map<String, Object>> channels = new ArrayList<>();
    for (Channels.ChannelInfo channel : mixer.channels()) {
      channels.add(full ? channelFull(channel) : channelSummary(channel));
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("channels", channels);
    payload.put("master", full ? masterFull(mixer.master()) : masterSummary(mixer.master()));
    payload.put("mixer", full ? mixerControls(mixer.controls()) : mixerSummary(mixer.controls()));
    return Result.ok(payload);
  }

  private static Map<String, Object> channelFull(Channels.ChannelInfo channel) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", channel.path());
    entry.put("id", channel.id());
    entry.put("label", channel.label());
    entry.put("index", channel.index());
    entry.put("type", channel.type().name().toLowerCase(Locale.ROOT));
    entry.put("enabled", channel.enabled());
    entry.put("fader", channel.fader());
    if (channel.groupPath() != null) {
      entry.put("group", channel.groupPath());
    }
    boolean blend = channel.patternMode() == Channels.PatternMode.BLEND;
    if (channel.type() == Channels.BusType.CHANNEL) {
      entry.put("patternMode", channel.patternMode().name().toLowerCase(Locale.ROOT));
    }
    entry.put("patterns", patterns(channel.patterns(), blend));
    entry.put("effects", effects(channel.effects()));
    entry.put("controls", channelControls(channel.controls()));
    return entry;
  }

  private static Map<String, Object> channelSummary(Channels.ChannelInfo channel) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", channel.path());
    entry.put("id", channel.id());
    entry.put("label", channel.label());
    entry.put("index", channel.index());
    entry.put("type", channel.type().name().toLowerCase(Locale.ROOT));
    entry.put("enabled", channel.enabled());
    entry.put("fader", channel.fader());
    if (channel.groupPath() != null) {
      entry.put("group", channel.groupPath());
    }
    if (channel.type() == Channels.BusType.CHANNEL) {
      entry.put("patternMode", channel.patternMode().name().toLowerCase(Locale.ROOT));
    }
    // 'active' is only meaningful in PLAYLIST mode — blend-mode channels and empty
    // channels carry no activePattern.
    if (channel.patternMode() == Channels.PatternMode.PLAYLIST) {
      channel.patterns().stream()
          .filter(Channels.PatternInfo::active)
          .findFirst()
          .ifPresent(active -> {
            Map<String, Object> activePattern = new LinkedHashMap<>();
            activePattern.put("path", active.path());
            activePattern.put("label", active.label());
            activePattern.put("class", active.className());
            entry.put("activePattern", activePattern);
          });
    }
    entry.put("patternCount", channel.patterns().size());
    entry.put("effectCount", channel.effects().size());
    return entry;
  }

  private static Map<String, Object> masterFull(Channels.MasterInfo master) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", master.path());
    entry.put("id", master.id());
    entry.put("label", master.label());
    entry.put("fader", master.fader());
    entry.put("effects", effects(master.effects()));
    return entry;
  }

  private static Map<String, Object> masterSummary(Channels.MasterInfo master) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", master.path());
    entry.put("id", master.id());
    entry.put("label", master.label());
    entry.put("fader", master.fader());
    entry.put("effectCount", master.effects().size());
    return entry;
  }

  private static Map<String, Object> mixerSummary(Channels.MixerControls controls) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("crossfader", field(controls.crossfader()));
    // Same object shape as full detail minus the long options array — a key whose JSON type
    // changed with `detail` would silently break clients reading .current or .path.
    entry.put("crossfaderBlendMode", enumField(controls.crossfaderBlendMode(), false));
    return entry;
  }

  private static Map<String, Object> field(Channels.Field<?> field) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("value", field.value());
    // path is null for read-only parameters that aren't path-registered (e.g.
    // isAutoMuted) — omit the key rather than emit a bogus "/null".
    if (field.path() != null) {
      entry.put("path", field.path());
    }
    return entry;
  }

  private static Map<String, Object> enumField(Channels.EnumField field) {
    return enumField(field, true);
  }

  private static Map<String, Object> enumField(Channels.EnumField field, boolean includeOptions) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("current", field.current());
    if (includeOptions && field.options() != null) {
      entry.put("options", field.options());
    }
    if (field.path() != null) {
      entry.put("path", field.path());
    }
    return entry;
  }

  private static Map<String, Object> channelControls(Channels.ChannelControls controls) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("crossfadeGroup", enumField(controls.crossfadeGroup()));
    entry.put("blendMode", enumField(controls.blendMode()));
    entry.put("autoMute", field(controls.autoMute()));
    entry.put("isAutoMuted", field(controls.isAutoMuted()));
    entry.put("cueActive", field(controls.cueActive()));
    entry.put("auxActive", field(controls.auxActive()));
    if (controls.patternEngine() != null) {
      entry.put("patternEngine", patternEngineControls(controls.patternEngine()));
    }
    return entry;
  }

  private static Map<String, Object> patternEngineControls(Channels.PatternEngineControls controls) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("autoCycleEnabled", field(controls.autoCycleEnabled()));
    entry.put("autoCycleMode", enumField(controls.autoCycleMode()));
    entry.put("autoCycleTimeSecs", field(controls.autoCycleTimeSecs()));
    entry.put("transitionEnabled", field(controls.transitionEnabled()));
    entry.put("transitionTimeSecs", field(controls.transitionTimeSecs()));
    entry.put("transitionBlendMode", enumField(controls.transitionBlendMode()));
    return entry;
  }

  private static Map<String, Object> mixerControls(Channels.MixerControls controls) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("crossfader", field(controls.crossfader()));
    entry.put("crossfaderBlendMode", enumField(controls.crossfaderBlendMode()));
    entry.put("cueA", field(controls.cueA()));
    entry.put("cueB", field(controls.cueB()));
    entry.put("auxA", field(controls.auxA()));
    entry.put("auxB", field(controls.auxB()));
    entry.put("blendModeOptions", controls.blendModeOptions());
    entry.put("transitionBlendModeOptions", controls.transitionBlendModeOptions());
    return entry;
  }

  private static List<Map<String, Object>> patterns(List<Channels.PatternInfo> patterns,
      boolean blend) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Channels.PatternInfo pattern : patterns) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", pattern.path());
      entry.put("id", pattern.id());
      entry.put("label", pattern.label());
      entry.put("class", pattern.className());
      entry.put("active", pattern.active());
      entry.put("enabled", pattern.enabled());
      if (blend) {
        entry.put("compositeLevel", pattern.compositeLevel());
      }
      entry.put("contributing", pattern.contributing());
      entry.put("effects", effects(pattern.effects()));
      result.add(entry);
    }
    return result;
  }

  private static List<Map<String, Object>> effects(List<Channels.EffectInfo> effects) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Channels.EffectInfo effect : effects) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", effect.path());
      entry.put("id", effect.id());
      entry.put("label", effect.label());
      entry.put("class", effect.className());
      entry.put("enabled", effect.enabled());
      result.add(entry);
    }
    return result;
  }
}
