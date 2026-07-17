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
    return "List one modulation engine's live modulators and wirings: modulator instances "
        + "(with OSC addresses), continuous modulations (with range/polarity and the "
        + "rangePath to adjust depth via set_parameter), and trigger wirings. Defaults to "
        + "the global engine; pass scope (a device path) for a pattern/effect's own chain. "
        + "Knob paths derive from a modulator's path (e.g. <path>/macro1).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("scope", Schemas.string(
        "Optional canonical path of a device (its own engine) or a modulation engine; "
            + "omit for the global engine"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object scope = args.get("scope");
    if (scope != null && !(scope instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "scope must be a string path");
    }
    Modulators.EngineInfo info =
        Modulators.listEngine(lx, Modulators.resolveEngine(lx, (String) scope));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("enginePath", info.path());
    List<Map<String, Object>> modulators = new ArrayList<>();
    for (Modulators.ModulatorInfo m : info.modulators()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", m.path());
      entry.put("id", m.id());
      entry.put("label", m.label());
      entry.put("class", m.className());
      entry.put("running", m.running());
      if (m.oscAddress() != null) {
        entry.put("oscAddress", m.oscAddress());
      }
      modulators.add(entry);
    }
    payload.put("modulators", modulators);
    List<Map<String, Object>> modulations = new ArrayList<>();
    for (Modulators.ModulationInfo m : info.modulations()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", m.path());
      entry.put("id", m.id());
      entry.put("sourcePath", m.sourcePath());
      entry.put("targetPath", m.targetPath());
      entry.put("range", m.range());
      entry.put("polarity", m.polarity());
      entry.put("rangePath", m.rangePath());
      modulations.add(entry);
    }
    payload.put("modulations", modulations);
    List<Map<String, Object>> triggers = new ArrayList<>();
    for (Modulators.TriggerInfo t : info.triggers()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", t.path());
      entry.put("id", t.id());
      entry.put("sourcePath", t.sourcePath());
      entry.put("targetPath", t.targetPath());
      triggers.add(entry);
    }
    payload.put("triggers", triggers);
    return Result.ok(payload);
  }
}
