package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulation.LXTriggerModulation;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;

import chromatikmcp.domain.Modulators;
import chromatikmcp.domain.Parameters;
import chromatikmcp.domain.Resolve;

public final class WireTrigger implements LxTool {

  @Override
  public String name() {
    return "wire_trigger";
  }

  @Override
  public String description() {
    return "Wire a trigger modulation: when the boolean source fires (e.g. a MacroTriggers "
        + "macro1), the boolean target is pulsed. Both ends must be boolean parameters. "
        + "Scope is inferred from the source like wire_modulator. Undoable in Chromatik "
        + "with Cmd-Z. Caution: a wiring LX rejects (circular dependency) clears "
        + "Chromatik's undo history.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("source", Schemas.string(
        "Canonical path of the boolean source parameter"));
    properties.put("target", Schemas.string(
        "Canonical path of the boolean target parameter"));
    properties.put("scope", Schemas.string(
        "Optional path of the engine hosting the wiring: a device path (its own engine) "
            + "or /lx/modulation (global — required to wire a device trigger to a target "
            + "outside its device). Omitted, it is inferred from the source"));
    return Schemas.object(properties, List.of("source", "target"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("source") instanceof String sourcePath)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: source");
    }
    if (!(args.get("target") instanceof String targetPath)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: target");
    }
    Object scope = args.get("scope");
    if (scope != null && !(scope instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "scope must be a string path");
    }
    LXParameter source = Resolve.parameter(lx, sourcePath);
    LXParameter target = Resolve.parameter(lx, targetPath);
    if (!(source instanceof BooleanParameter booleanSource)) {
      return Result.error(Result.INVALID_ARGUMENT, "Trigger source " + sourcePath
          + " (" + source.getClass().getSimpleName() + ") must be a boolean parameter");
    }
    if (!(target instanceof BooleanParameter booleanTarget)) {
      return Result.error(Result.INVALID_ARGUMENT, "Trigger target " + targetPath
          + " (" + target.getClass().getSimpleName() + ") must be a boolean parameter");
    }
    Parameters.requireWritable(booleanTarget);
    LXModulationEngine engine = Modulators.selectEngine(lx, (String) scope, source);
    LXTriggerModulation trigger =
        Modulators.wireTrigger(lx, engine, booleanSource, booleanTarget);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", trigger.getCanonicalPath());
    payload.put("sourcePath", source.getCanonicalPath());
    payload.put("targetPath", target.getCanonicalPath());
    payload.put("enginePath", engine.getCanonicalPath());
    return Result.ok(payload);
  }
}
