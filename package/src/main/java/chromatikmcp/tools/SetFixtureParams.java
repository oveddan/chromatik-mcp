package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Resolve;

public final class SetFixtureParams implements LxTool {

  @Override
  public String name() {
    return "set_fixture_params";
  }

  @Override
  public String description() {
    return "Set several of a fixture's parameters in one call — both its registered "
        + "parameters (x/y/z/yaw/pitch/roll/scale, enabled, brightness, numPoints, "
        + "artNetUniverse, host, and any type-specific ones, e.g. a GridFixture's "
        + "numRows — otherwise settable one at a time via set_parameter) and, for a "
        + "JsonFixture (a fixture loaded from a .lxf file), the knobs its 'parameters' "
        + "block declares (e.g. controller IP strings, per-controller booleans, geometry "
        + "floats) — these JSON parameters have no canonical path, so set_parameter cannot "
        + "reach them; this tool is their only write path, addressed by name (see "
        + "get_fixture's 'jsonParameters'). Every name is resolved and every value "
        + "type-checked before anything is written — an unknown name or a type mismatch on "
        + "any one entry leaves the fixture completely untouched, nothing partially applies. "
        + "The WRITE itself is not atomic across a mixed numeric+string call, though: the "
        + "numeric/boolean edits (batched into a single undo entry) are always performed "
        + "before the string edits (one undo entry each, reported in 'undoEntries'), so if a "
        + "string write fails partway through, earlier writes stay applied — and LX clears "
        + "its entire undo/redo stack when any command fails, not just that entry. Batch "
        + "related edits into one call rather than calling this repeatedly regardless. Each "
        + "parameter change triggers a full model rebuild (re-point, re-normalize, rebuild "
        + "every view, plus a synchronous System.gc()), and a JSON parameter write "
        + "additionally re-reads the fixture's .lxf from disk — another reason to batch. "
        + "Never drive a continuous control (e.g. an LFO) into a fixture parameter this way; "
        + "it is metrics/placement/tag data, not a render input. Rejected on a subfixture of "
        + "a JsonFixture (its values are computed from the .lxf and recomputed on reload — "
        + "edit the .lxf and call reload_fixtures instead); a top-level .lxf fixture's own "
        + "parameters (registered or JSON) are the intended edit surface. Registered "
        + "parameters are resolved before same-named .lxf-declared ones — a .lxf may legally "
        + "declare a parameter with the same name as a registered one (e.g. 'scale'), in "
        + "which case the registered parameter is written and the JSON one is left untouched; "
        + "such names are reported in 'shadowedJsonParams' (present only when non-empty).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string("Canonical path of the fixture, e.g. /lx/structure/fixture/1"));
    properties.put("params", Map.of(
        "type", "object",
        "description", "Map of parameter name -> new value. Registered parameters are "
            + "looked up first, then (for a JsonFixture) its .lxf-declared parameters by "
            + "name. Value type must match the parameter: a number for numeric/discrete, "
            + "a boolean for toggles, a string for text.",
        "additionalProperties", Map.of("type", List.of("number", "boolean", "string"))));
    return Schemas.object(properties, List.of("path", "params"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object pathArg = args.get("path");
    if (!(pathArg instanceof String path) || path.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "path must be a non-empty string");
    }
    Object paramsArg = args.get("params");
    if (!(paramsArg instanceof Map)) {
      return Result.error(Result.INVALID_ARGUMENT, "params must be an object");
    }
    Map<String, Object> params = (Map<String, Object>) paramsArg;
    if (params.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "params must not be empty");
    }

    LXFixture fixture = Resolve.component(lx, path, LXFixture.class);
    Fixtures.SetParamsResult result = Fixtures.setParams(lx, fixture, params);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", Resolve.canonicalPath(fixture));
    payload.put("undoEntries", result.undoEntries());
    payload.put("params", result.values());
    if (!result.shadowedJsonParams().isEmpty()) {
      payload.put("shadowedJsonParams", result.shadowedJsonParams());
    }
    return Result.ok(payload);
  }
}
