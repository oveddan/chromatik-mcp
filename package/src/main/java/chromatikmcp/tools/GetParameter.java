package chromatikmcp.tools;

import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Parameters;

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
    return Result.ok(info.toMap());
  }
}
