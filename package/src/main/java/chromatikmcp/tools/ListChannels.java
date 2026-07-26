package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Channels;
import chromatikmcp.domain.Views;

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
        + "effectCount, containerPatternCount, anyLocalModulation, view) that is the right choice "
        + "for surveying a project; a real project can carry dozens of channels and hundreds "
        + "of patterns/effects, and the full shape blows past client response limits. Pass "
        + "'detail: full' for today's complete shape (controls block, full patterns array "
        + "with per-pattern effects, effects array). "
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
        + "Every channel entry — at both detail levels, including summary — carries "
        + "'containerPatternCount' and 'anyLocalModulation' as channel-wide rollups: they "
        + "tell you whether hidden structure exists ANYWHERE on the channel, without walking "
        + "past what this tool already lists. 'containerPatternCount' is how many of the "
        + "channel's direct patterns are themselves a container (e.g. a PatternRack) whose "
        + "own child patterns are not in this payload at any detail level — distinct from the "
        + "channel-level 'patternCount', which counts a channel's own direct patterns, not how "
        + "many of them are containers; do not conflate the two. 'anyLocalModulation' is true "
        + "iff any pattern or effect on the channel owns a non-empty device-local modulation "
        + "engine. Neither rollup says WHICH pattern or effect — for that, use 'detail: full', "
        + "which puts a 'nestedPatternCount' and 'hasLocalModulation' marker on every pattern "
        + "and effect entry (call list_parameters on a pattern's path for its 'children' array "
        + "of nested pattern paths, and list_modulations with scope=<that path> for its local "
        + "modulation). In summary detail, only the single active pattern in PLAYLIST mode "
        + "(the 'activePattern' object) carries these per-pattern markers directly — for every "
        + "other pattern, and for effects (no summary-mode entry; only 'effectCount'), the "
        + "channel-level rollups are the only summary-detail signal that more exists. The "
        + "master bus carries 'anyLocalModulation' too (it can only host effects, no "
        + "patterns, so it has no 'containerPatternCount'). "
        + "Every channel, pattern, and effect entry — at both detail levels for channels; "
        + "pattern/effect entries only exist at 'detail: full', plus the summary-mode "
        + "'activePattern' object — carries a 'view' object reporting its model-view "
        + "assignment when there is something to report (the master bus itself never has a "
        + "'view' key; it has no view selector, though its own effects do). An entry with NO "
        + "'view' key is on Default and renders to the whole model — the settable selector "
        + "for any channel, pattern, or effect entry is always that entry's own 'path' + "
        + "'/view', so no key is needed to address it via set_parameter (the master bus is "
        + "the one exception: it has no view selector at all, so no such path exists for "
        + "it). When 'view' IS present: 'selected'/'selectedPath' "
        + "are omitted when the selector is on 'Default' (inherits from its parent: a channel "
        + "inherits from its group, a pattern/effect inherits from its host channel or "
        + "pattern; a master-bus effect on Default inherits the whole model, because the "
        + "master bus has no view of its own to inherit — but it can set its own view, and "
        + "then renders to that); 'effective'/"
        + "'effectivePath' are omitted when this component renders to the whole model. "
        + "Read the combinations: 'selected' present and 'effective' present = this entry "
        + "sets its own view (they are always the same view in this case); 'selected' absent "
        + "but 'effective' present = it is on Default and inherited that view from its "
        + "parent; 'selected' present but 'effective' absent = LX built no view object for "
        + "the selected view, so this entry falls back to the whole model. LX builds one only "
        + "when the model is non-empty AND the view is enabled AND its selector string is "
        + "non-blank — if any of those fails, everything pointing at that view falls back. "
        + "That is NOT the same as 'the view matches no fixtures': an enabled view with a "
        + "non-blank selector in a non-empty model still gets a view object even when it "
        + "currently matches zero fixtures, so 'effective' stays set to it and get_views "
        + "reports it with numGroups/numFixtures of 0 — detect 'assigned to a view that "
        + "lights nothing' by checking those counts via 'effectivePath', never by "
        + "presence/absence. A pattern's own 'view' can "
        + "override its channel's — do not assume a channel's view describes what its active "
        + "pattern renders to; check the pattern's own 'view' (or 'activePattern.view' in "
        + "summary detail). 'effectivePath' joins to get_views, which reports each view's "
        + "'selector', 'numGroups', 'numFixtures', 'normalization', and 'orientation' — a "
        + "pattern whose 'effective' view differs from its channel's may normalize its "
        + "coordinates differently (not just light a different set of points), so compare "
        + "'normalization'/'orientation' between the two views before reasoning about a "
        + "pattern's coordinate-space behavior. "
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

  // Package-private: reused by GetChannel so the two tools' single-channel/master
  // payload shape stays identical (same rationale as GetFixture / ListFixtures.toMap).
  static Map<String, Object> channelFull(Channels.ChannelInfo channel) {
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
    entry.put("containerPatternCount", channel.containerPatternCount());
    entry.put("anyLocalModulation", channel.anyLocalModulation());
    Map<String, Object> view = viewRefOrNull(channel.view());
    if (view != null) {
      entry.put("view", view);
    }
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
            activePattern.put("nestedPatternCount", active.nestedPatternCount());
            activePattern.put("hasLocalModulation", active.hasLocalModulation());
            Map<String, Object> activeView = viewRefOrNull(active.view());
            if (activeView != null) {
              activePattern.put("view", activeView);
            }
            entry.put("activePattern", activePattern);
          });
    }
    entry.put("patternCount", channel.patterns().size());
    entry.put("effectCount", channel.effects().size());
    entry.put("containerPatternCount", channel.containerPatternCount());
    entry.put("anyLocalModulation", channel.anyLocalModulation());
    Map<String, Object> view = viewRefOrNull(channel.view());
    if (view != null) {
      entry.put("view", view);
    }
    return entry;
  }

  static Map<String, Object> masterFull(Channels.MasterInfo master) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", master.path());
    entry.put("id", master.id());
    entry.put("label", master.label());
    entry.put("fader", master.fader());
    entry.put("effects", effects(master.effects()));
    entry.put("anyLocalModulation", master.anyLocalModulation());
    return entry;
  }

  private static Map<String, Object> masterSummary(Channels.MasterInfo master) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", master.path());
    entry.put("id", master.id());
    entry.put("label", master.label());
    entry.put("fader", master.fader());
    entry.put("effectCount", master.effects().size());
    entry.put("anyLocalModulation", master.anyLocalModulation());
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

  // See the tool description for how to read the presence/absence of each key: 'selected'
  // absent means Default (inherits); 'effective' absent means "renders to the whole model".
  // Returns null (key omitted entirely) when both are absent — selectorPath alone is pure
  // derivable string (<entry path> + "/view") and not worth a key on every entry.
  private static Map<String, Object> viewRefOrNull(Views.ViewRef view) {
    if (view.selectedLabel() == null && view.effectiveLabel() == null) {
      return null;
    }
    Map<String, Object> entry = new LinkedHashMap<>();
    if (view.selectorPath() != null) {
      entry.put("selectorPath", view.selectorPath());
    }
    if (view.selectedLabel() != null) {
      entry.put("selected", view.selectedLabel());
      entry.put("selectedPath", view.selectedPath());
    }
    if (view.effectiveLabel() != null) {
      entry.put("effective", view.effectiveLabel());
      entry.put("effectivePath", view.effectivePath());
    }
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
      // Nonzero iff this pattern is itself a container (e.g. a PatternRack) whose own
      // children are not in this payload — distinct from the channel-level patternCount
      // above, which counts this channel's own direct patterns, not a pattern's nested
      // ones. list_parameters on this pattern's path returns those children today.
      entry.put("nestedPatternCount", pattern.nestedPatternCount());
      entry.put("hasLocalModulation", pattern.hasLocalModulation());
      Map<String, Object> view = viewRefOrNull(pattern.view());
      if (view != null) {
        entry.put("view", view);
      }
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
      entry.put("hasLocalModulation", effect.hasLocalModulation());
      Map<String, Object> view = viewRefOrNull(effect.view());
      if (view != null) {
        entry.put("view", view);
      }
      result.add(entry);
    }
    return result;
  }
}
