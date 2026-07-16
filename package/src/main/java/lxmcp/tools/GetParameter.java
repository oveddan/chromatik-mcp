package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Parameters;

public final class GetParameter implements LxTool {

  @Override
  public String name() {
    return "get_parameter";
  }

  @Override
  public String description() {
    return "Read one parameter by its canonical LX path (e.g. /lx/mixer/channel/1/fader): "
        + "value, type, range, options, and units. For a parameter with live modulations, "
        + "value is the current effective (modulated) reading, baseValue is the knob's set "
        + "position, and modulated=true; set_parameter changes the base.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("path", Schemas.string(
            "Canonical LX path of the parameter, as returned by the list/get tools")),
        List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("path") instanceof String path)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    // Resolution failures are typed ResolveExceptions; the seam maps them to wire codes.
    Parameters.ParameterInfo info = Parameters.get(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", info.path());
    payload.put("label", info.label());
    if (info.description() != null) {
      payload.put("description", info.description());
    }
    payload.put("type", info.type());
    payload.put("value", info.value());
    if (info.normalized() != null) {
      payload.put("normalized", info.normalized());
    }
    payload.put("units", info.units());
    if (info.min() != null) {
      payload.put("min", info.min());
    }
    if (info.max() != null) {
      payload.put("max", info.max());
    }
    if (info.options() != null) {
      payload.put("options", info.options());
    }
    if (info.formatted() != null) {
      payload.put("formatted", info.formatted());
    }
    if (info.oscAddress() != null) {
      payload.put("oscAddress", info.oscAddress());
    }
    if (info.modulated()) {
      payload.put("modulated", true);
      payload.put("baseValue", info.baseValue());
      if (info.baseNormalized() != null) {
        payload.put("baseNormalized", info.baseNormalized());
      }
    }
    return Result.ok(payload);
  }
}
