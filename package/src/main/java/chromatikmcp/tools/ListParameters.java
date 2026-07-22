package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Parameters;

public final class ListParameters implements LxTool {

  @Override
  public String name() {
    return "list_parameters";
  }

  @Override
  public String description() {
    return "List every parameter on the component at a canonical LX path (channel, pattern, "
        + "effect, modulator, or engine component like the output engine) — names, types, "
        + "ranges, current values, and each parameter's own canonical path for "
        + "get_parameter/set_parameter. Use this instead of guessing parameter names. Parameters "
        + "with live modulations additionally carry baseValue and modulated=true (value is the "
        + "effective reading). Also lists the component's child components (a pattern's effects, "
        + "the palette's swatches, a channel's patterns) with their canonical paths — use it to "
        + "walk the component tree instead of guessing paths.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical LX path of the component, as returned by the list/get tools"));
    return Schemas.object(properties, List.of("path"));
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
    Parameters.ComponentParameters info = Parameters.listFor(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", info.path());
    payload.put("id", info.id());
    payload.put("label", info.label());
    payload.put("class", info.className());
    List<Map<String, Object>> parameters = new ArrayList<>();
    for (Parameters.ParameterInfo p : info.parameters()) {
      parameters.add(p.toMap());
    }
    payload.put("parameters", parameters);
    List<Map<String, Object>> children = new ArrayList<>();
    for (Parameters.ChildInfo c : info.children()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("key", c.key());
      entry.put("path", c.path());
      entry.put("label", c.label());
      entry.put("class", c.className());
      children.add(entry);
    }
    payload.put("children", children);
    return Result.ok(payload);
  }
}
