package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

import chromatikmcp.domain.Modulators;
import chromatikmcp.domain.Resolve;

public final class WireModulator implements LxTool {

  @Override
  public String name() {
    return "wire_modulator";
  }

  @Override
  public String description() {
    return "Wire a continuous modulation from a source parameter (e.g. a macro knob's "
        + "macro1) onto a target parameter. To use an oscillator/envelope modulator's own "
        + "running value as the source, pass the modulator's own canonical path (it is "
        + "itself a parameter) — not one of its input sub-parameters like an LFO's basisIn, "
        + "which only takes effect when that modulator's manualBasis is enabled and "
        + "otherwise silently does nothing. The target must be a compound parameter (most "
        + "device/mixer knobs are). Scope is inferred from the source — a knob inside a "
        + "device chain wires within that device; pass scope explicitly to override. The "
        + "wiring starts with zero depth and has no visible effect until depth is set — "
        + "pass the optional range argument (e.g. 1.0 for full depth) to apply it "
        + "immediately, or set_parameter on the returned rangePath afterwards. Adjust "
        + "direction via polarityPath. Undoable in Chromatik with Cmd-Z, though a wiring "
        + "created with range takes two undo steps (depth first, then the wiring). Caution: a wiring "
        + "LX rejects (circular dependency) clears Chromatik's undo history.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("source", Schemas.string(
        "Canonical path of the source parameter (e.g. a MacroKnobs macro1)"));
    properties.put("target", Schemas.string(
        "Canonical path of the target compound parameter"));
    properties.put("scope", Schemas.string(
        "Optional path of the engine hosting the wiring: a device path (its own engine) "
            + "or /lx/modulation (global — required to wire a device knob to a target "
            + "outside its device). Omitted, it is inferred from the source"));
    properties.put("range", Map.of(
        "type", "number",
        "description", "Optional initial modulation depth, -1.0 to 1.0; without it the "
            + "wiring starts at 0 and is inert"));
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
    Double range = null;
    Object rangeArg = args.get("range");
    if (rangeArg != null) {
      if (!(rangeArg instanceof Number number)) {
        return Result.error(Result.INVALID_ARGUMENT, "range must be a number");
      }
      range = number.doubleValue();
      if (!(range >= -1.0 && range <= 1.0)) {
        return Result.error(Result.INVALID_ARGUMENT, "range must be between -1.0 and 1.0");
      }
    }
    LXParameter source = Resolve.parameter(lx, sourcePath);
    LXParameter target = Resolve.parameter(lx, targetPath);
    if (!(source instanceof LXNormalizedParameter normalizedSource)) {
      return Result.error(Result.INVALID_ARGUMENT, "Source " + sourcePath
          + " (" + source.getClass().getSimpleName() + ") is not a normalized parameter");
    }
    if (!(target instanceof LXCompoundModulation.Target compoundTarget)) {
      return Result.error(Result.INVALID_ARGUMENT, "Target " + targetPath
          + " (" + target.getClass().getSimpleName() + ") cannot receive continuous "
          + "modulation — only compound parameters can");
    }
    LXModulationEngine engine = Modulators.selectEngine(lx, (String) scope, source);
    LXCompoundModulation modulation =
        Modulators.wireModulation(lx, engine, normalizedSource, compoundTarget, range);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", modulation.getCanonicalPath());
    payload.put("sourcePath", source.getCanonicalPath());
    payload.put("targetPath", target.getCanonicalPath());
    payload.put("enginePath", engine.getCanonicalPath());
    payload.put("rangePath", modulation.range.getCanonicalPath());
    payload.put("polarityPath", modulation.polarity.getCanonicalPath());
    payload.put("range", modulation.range.getValue());
    return Result.ok(payload);
  }
}
