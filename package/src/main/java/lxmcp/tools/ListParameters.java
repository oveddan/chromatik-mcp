package lxmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Parameters;

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
        + "get_parameter/set_parameter. Use this instead of guessing parameter names.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("path", Schemas.string(
            "Canonical LX path of the component, as returned by the list/get tools")),
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
    Parameters.ComponentParameters info = Parameters.listFor(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", info.path());
    payload.put("id", info.id());
    payload.put("label", info.label());
    payload.put("class", info.className());
    List<Map<String, Object>> parameters = new ArrayList<>();
    for (Parameters.ParameterInfo p : info.parameters()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", p.path());
      entry.put("label", p.label());
      if (p.description() != null) {
        entry.put("description", p.description());
      }
      entry.put("type", p.type());
      entry.put("value", p.value());
      if (p.normalized() != null) {
        entry.put("normalized", p.normalized());
      }
      entry.put("units", p.units());
      if (p.min() != null) {
        entry.put("min", p.min());
      }
      if (p.max() != null) {
        entry.put("max", p.max());
      }
      if (p.options() != null) {
        entry.put("options", p.options());
      }
      if (p.formatted() != null) {
        entry.put("formatted", p.formatted());
      }
      if (p.oscAddress() != null) {
        entry.put("oscAddress", p.oscAddress());
      }
      parameters.add(entry);
    }
    payload.put("parameters", parameters);
    return Result.ok(payload);
  }
}
