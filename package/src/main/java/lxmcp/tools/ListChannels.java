package lxmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Channels;

public final class ListChannels implements LxTool {

  @Override
  public String name() {
    return "list_channels";
  }

  @Override
  public String description() {
    return "List the mixer's channels with their patterns and effects, plus the master bus. "
        + "Every entry carries its canonical LX path for use with other tools. "
        + "Channels have two pattern modes ('patternMode'): 'playlist' plays one pattern at a "
        + "time — the one with active=true; 'blend' composites all patterns simultaneously — a "
        + "pattern shows iff enabled=true AND compositeLevel > 0 ('active' is not meaningful in "
        + "blend mode). In playlist mode 'enabled' only affects auto-cycle eligibility — it does "
        + "not hide the active pattern. The per-pattern 'contributing' field applies the correct "
        + "rule for the channel's mode. A contributing pattern is still invisible if its channel is disabled "
        + "or its fader is 0, or if engine output is off (see get_project_info). "
        + "Patterns can host their own effect chains — each pattern entry carries its own "
        + "effects list (e.g. a Gradient Mask living inside a pattern rather than on the "
        + "channel). "
        + "The top-level 'mixer' object is the crossfader performance surface: 'crossfader' "
        + "runs 0 (full A) to 1 (full B) — only channels whose 'controls.crossfadeGroup' is "
        + "'A' or 'B' (not 'BYPASS') are affected by it, blended via 'crossfaderBlendMode'. "
        + "'cueA'/'cueB'/'auxA'/'auxB' toggle the crossfade-group preview buses: cue is the "
        + "primary preview output, aux is a secondary/independent preview output — neither "
        + "affects the main program output. Per-channel and per-pattern-engine blend-mode "
        + "option lists are identical across channels, so they are reported once at "
        + "'mixer.blendModeOptions' / 'mixer.transitionBlendModeOptions' rather than repeated "
        + "on every channel. Each channel's 'controls' block carries its crossfade-group "
        + "assignment, blend mode, auto-mute state, and cue/aux preview toggles; "
        + "'controls.patternEngine' (playlist/blend channels only — absent on groups) carries "
        + "auto-cycle and pattern-transition settings, with 'set_parameter' as the mutation "
        + "path for any of these fields via the accompanying canonical 'path'.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Channels.MixerInfo mixer = Channels.list(lx);

    List<Map<String, Object>> channels = new ArrayList<>();
    for (Channels.ChannelInfo channel : mixer.channels()) {
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
      channels.add(entry);
    }

    Map<String, Object> master = new LinkedHashMap<>();
    master.put("path", mixer.master().path());
    master.put("id", mixer.master().id());
    master.put("label", mixer.master().label());
    master.put("fader", mixer.master().fader());
    master.put("effects", effects(mixer.master().effects()));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("channels", channels);
    payload.put("master", master);
    payload.put("mixer", mixerControls(mixer.controls()));
    return Result.ok(payload);
  }

  private static Map<String, Object> field(Channels.Field<?> field) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("value", field.value());
    entry.put("path", field.path());
    return entry;
  }

  private static Map<String, Object> enumField(Channels.EnumField field) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("current", field.current());
    if (field.options() != null) {
      entry.put("options", field.options());
    }
    entry.put("path", field.path());
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
