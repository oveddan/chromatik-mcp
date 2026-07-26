package chromatikmcp.tools;

import java.util.LinkedHashMap;
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
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical LX path of the parameter, as returned by the list/get tools"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    // Resolution failures are typed ResolveExceptions; the seam maps them to wire codes.
    Parameters.ParameterInfo info = Parameters.get(lx, path);
    return Result.ok(info.toMap());
  }
}
