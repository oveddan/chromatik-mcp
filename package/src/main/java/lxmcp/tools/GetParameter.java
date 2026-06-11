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
        + "value, type, range, options, and units.";
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
    if (!(args.get("path") instanceof String path) || path.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    Parameters.ParameterInfo info = Parameters.get(lx, path);
    if (info == null) {
      return Result.error(Result.NOT_FOUND, "No parameter at path: " + path);
    }
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
    payload.put("formatted", info.formatted());
    return Result.ok(payload);
  }
}
