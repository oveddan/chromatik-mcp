package lxmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.parameter.LXParameter;

import lxmcp.domain.Modulators;

public final class AddModulator implements LxTool {

  @Override
  public String name() {
    return "add_modulator";
  }

  @Override
  public String description() {
    return "Add a modulator by class name (from list_available_modulators) — e.g. "
        + "heronarts.lx.modulator.MacroKnobs for a bank of eight mappable knobs. By default "
        + "it lands in the global modulation engine (the Chromatik side panel); pass scope "
        + "to add it inside a pattern/effect's own chain. The response lists every parameter "
        + "with its canonical path and OSC address. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", Schemas.string(
        "Modulator class name, as returned by list_available_modulators"));
    properties.put("scope", Schemas.string(
        "Optional canonical path of a pattern/effect to host the modulator in its own "
            + "chain; omit for the global engine"));
    return Schemas.object(properties, List.of("type"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("type") instanceof String type)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: type");
    }
    Object scope = args.get("scope");
    if (scope != null && !(scope instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "scope must be a string path");
    }
    LXModulationEngine engine = Modulators.resolveEngine(lx, (String) scope);
    LXModulator modulator = Modulators.addModulator(lx, engine,
        Modulators.resolveModulatorClass(lx, type));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", modulator.getCanonicalPath());
    payload.put("id", modulator.getId());
    payload.put("label", modulator.getLabel());
    payload.put("class", modulator.getClass().getName());
    payload.put("running", modulator.isRunning());
    if (modulator.getOscAddress() != null) {
      payload.put("oscAddress", modulator.getOscAddress());
    }
    List<Map<String, Object>> parameters = new ArrayList<>();
    for (LXParameter parameter : modulator.getParameters()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", parameter.getCanonicalPath());
      entry.put("label", parameter.getLabel());
      entry.put("type", parameter.getClass().getSimpleName());
      String oscAddress = LXOscEngine.getOscAddress(parameter);
      if (oscAddress != null) {
        entry.put("oscAddress", oscAddress);
      }
      parameters.add(entry);
    }
    payload.put("parameters", parameters);
    return Result.ok(payload);
  }
}
