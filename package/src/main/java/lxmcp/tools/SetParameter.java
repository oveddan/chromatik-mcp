package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Parameters;

public final class SetParameter implements LxTool {

  @Override
  public String name() {
    return "set_parameter";
  }

  @Override
  public String description() {
    return "Set a parameter by its canonical LX path (e.g. /lx/mixer/channel/1/fader). "
        + "The value's type must match the parameter: a number for numeric/bounded, a "
        + "boolean for toggles, a string for text. Discrete/selector parameters accept "
        + "either the in-range integer value or an option name string (e.g. a device's "
        + "'view' selector accepts the target view's label) — an unknown or ambiguous "
        + "option name is rejected with the valid options listed. "
        + "Aggregate parameters (color, MIDI filter) are set via their component paths "
        + "(e.g. .../hue, .../saturation, .../brightness); momentary triggers fire via "
        + "fire_trigger. Undoable in Chromatik with Cmd-Z. On a parameter with live modulations "
        + "the response's value is the effective (modulated) reading — baseValue echoes the value you set.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical LX path of the parameter, as returned by the list/get tools"));
    properties.put("value", Map.of(
        "description", "New value; its type must match the parameter (number, boolean, or string)",
        "type", List.of("number", "boolean", "string")));
    return Schemas.object(properties, List.of("path", "value"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("path") instanceof String path)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    if (!args.containsKey("value")) {
      return Result.error(Result.INVALID_ARGUMENT, "Required argument: value");
    }
    // Resolution and value-type failures are typed ResolveExceptions; the seam maps them.
    // A live modulation rides on top of the value just set — the shared payload surfaces
    // both modulated/baseValue so the caller doesn't mistake the effective reading for
    // the base position it wrote.
    Parameters.ParameterInfo info = Parameters.set(lx, path, args.get("value"));
    return Result.ok(info.toMap());
  }
}
