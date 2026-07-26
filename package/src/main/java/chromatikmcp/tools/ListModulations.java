package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Modulators;

public final class ListModulations implements LxTool {

  @Override
  public String name() {
    return "list_modulations";
  }

  @Override
  public String description() {
    return "List one modulation engine's live modulators and wirings. Defaults to "
        + "'detail: summary' — the wiring graph only (modulators: path/label/class; "
        + "modulations and triggers: path/sourcePath/targetPath) — the right choice for surveying a "
        + "project; a real project can carry dozens of modulators and hundreds of wirings, and "
        + "the full shape blows past client response limits. Pass 'detail: full' for today's "
        + "complete shape (modulator OSC addresses/running state, and per-modulation "
        + "range/polarity/rangePath to adjust depth via set_parameter). Defaults to the global "
        + "engine; pass scope (a device path) for a pattern/effect's own chain. Knob paths "
        + "derive from a modulator's path (e.g. <path>/macro1).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("scope", Schemas.string(
        "Optional canonical path of a device (its own engine) or a modulation engine; "
            + "omit for the global engine"));
    properties.put("detail", Schemas.enumString(
        "'summary' (default) for the wiring graph only, or 'full' for today's complete "
            + "payload (OSC addresses, running state, range/polarity/rangePath)",
        List.of("summary", "full")));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String scope = Args.optionalString(args, "scope", "scope must be a string path");
    String detail = Args.optionalString(args, "detail");
    if (detail != null && !detail.equals("summary") && !detail.equals("full")) {
      return Result.error(Result.INVALID_ARGUMENT, "detail must be 'summary' or 'full'");
    }
    boolean full = "full".equals(detail);

    Modulators.EngineInfo info =
        Modulators.listEngine(lx, Modulators.resolveEngine(lx, scope));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("enginePath", info.path());

    List<Map<String, Object>> modulators = new ArrayList<>();
    for (Modulators.ModulatorInfo m : info.modulators()) {
      modulators.add(full ? modulatorFull(m) : modulatorSummary(m));
    }
    payload.put("modulators", modulators);

    List<Map<String, Object>> modulations = new ArrayList<>();
    for (Modulators.ModulationInfo m : info.modulations()) {
      modulations.add(full ? modulationFull(m) : modulationSummary(m));
    }
    payload.put("modulations", modulations);

    List<Map<String, Object>> triggers = new ArrayList<>();
    for (Modulators.TriggerInfo t : info.triggers()) {
      triggers.add(full ? triggerFull(t) : triggerSummary(t));
    }
    payload.put("triggers", triggers);

    return Result.ok(payload);
  }

  private static Map<String, Object> modulatorFull(Modulators.ModulatorInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("id", m.id());
    entry.put("label", m.label());
    entry.put("class", m.className());
    entry.put("running", m.running());
    if (m.oscAddress() != null) {
      entry.put("oscAddress", m.oscAddress());
    }
    return entry;
  }

  private static Map<String, Object> modulatorSummary(Modulators.ModulatorInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("label", m.label());
    entry.put("class", m.className());
    return entry;
  }

  private static Map<String, Object> modulationFull(Modulators.ModulationInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("id", m.id());
    entry.put("sourcePath", m.sourcePath());
    entry.put("targetPath", m.targetPath());
    entry.put("range", m.range());
    entry.put("polarity", m.polarity());
    entry.put("rangePath", m.rangePath());
    return entry;
  }

  private static Map<String, Object> modulationSummary(Modulators.ModulationInfo m) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", m.path());
    entry.put("sourcePath", m.sourcePath());
    entry.put("targetPath", m.targetPath());
    return entry;
  }

  private static Map<String, Object> triggerFull(Modulators.TriggerInfo t) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", t.path());
    entry.put("id", t.id());
    entry.put("sourcePath", t.sourcePath());
    entry.put("targetPath", t.targetPath());
    return entry;
  }

  private static Map<String, Object> triggerSummary(Modulators.TriggerInfo t) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", t.path());
    entry.put("sourcePath", t.sourcePath());
    entry.put("targetPath", t.targetPath());
    return entry;
  }
}
