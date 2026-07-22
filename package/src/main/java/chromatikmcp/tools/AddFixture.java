package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;

public final class AddFixture implements LxTool {

  @Override
  public String name() {
    return "add_fixture";
  }

  @Override
  public String description() {
    return "Instantiate a fixture (see list_available_fixtures for what's addable) — "
        + "exactly one of 'class' (a built-in fixture type, e.g. GridFixture) or 'type' (a "
        + ".lxf file's type string, e.g. MyRig/Cube) must be given. 'index' (0-based, "
        + "clamped to the current fixture count) inserts at that position in "
        + "lx.structure.fixtures instead of appending at the end — supported with 'class' "
        + "only; 'type' always appends, and combining 'type' with 'index' is rejected "
        + "(add the fixture, then reposition it with move_fixture). 'label' and 'params' "
        + "(registered parameters only — x/y/z, artNetUniverse, a type-specific one like "
        + "GridFixture's numRows, etc; NOT a JsonFixture's .lxf-declared parameters, which "
        + "have no value until the fixture loads — configure those afterwards with "
        + "set_fixture_params) are applied right after the add, and the whole call — "
        + "instantiate plus configure — is a single undo step. Every fixture path is "
        + "POSITIONAL (/lx/structure/fixture/N, 1-indexed) and shifts after any later "
        + "add/remove/move — re-list with list_fixtures rather than reusing a path from an "
        + "earlier response. Rejected when the structure is in static-model mode.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("class", Schemas.string(
        "Built-in fixture class, simple or full name (see list_available_fixtures' "
            + "'classes'), e.g. 'GridFixture'. Exactly one of class/type is required."));
    properties.put("type", Schemas.string(
        "A .lxf fixture type string (see list_available_fixtures' 'jsonTypes'), e.g. "
            + "'MyRig/Cube'. Exactly one of class/type is required."));
    properties.put("index", Map.of(
        "type", "integer",
        "description", "0-based insert position in lx.structure.fixtures; omit to append "
            + "at the end. Clamped into range. Supported with 'class' only — 'type' always "
            + "appends, and 'type' + 'index' together is rejected."));
    properties.put("label", Schemas.string("Optional display label; overrides LX's default "
        + "auto-suffixed label (e.g. 'Grid 2')."));
    properties.put("params", Map.of(
        "type", "object",
        "description", "Optional map of registered parameter name -> initial value, applied "
            + "right after the add (folded into the same undo step). Value type must match "
            + "the parameter: a number for numeric/discrete, a boolean for toggles, a "
            + "string for text.",
        "additionalProperties", Map.of("type", List.of("number", "boolean", "string"))));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object classArg = args.get("class");
    Object typeArg = args.get("type");
    boolean hasClass = classArg != null;
    boolean hasType = typeArg != null;
    if (hasClass == hasType) {
      return Result.error(Result.INVALID_ARGUMENT,
          "Exactly one of 'class' or 'type' is required (both given: " + hasClass
              + ", neither given: " + !hasClass + ")");
    }
    if (hasClass && !(classArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "class must be a string");
    }
    if (hasType && !(typeArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "type must be a string");
    }

    Integer index = null;
    if (args.containsKey("index")) {
      Object indexArg = args.get("index");
      if (!(indexArg instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
        return Result.error(Result.INVALID_ARGUMENT, "index must be an integer");
      }
      index = number.intValue();
    }

    if (hasType && index != null) {
      return Result.error(Result.INVALID_ARGUMENT,
          "add_fixture with 'type' appends the fixture; to place a .lxf fixture at a "
              + "specific position, add it then call move_fixture, or use 'class' with "
              + "'index'.");
    }

    String label = null;
    if (args.containsKey("label")) {
      if (!(args.get("label") instanceof String labelArg) || labelArg.isEmpty()) {
        return Result.error(Result.INVALID_ARGUMENT, "label must be a non-empty string");
      }
      label = labelArg;
    }

    Map<String, Object> params = null;
    if (args.containsKey("params")) {
      if (!(args.get("params") instanceof Map)) {
        return Result.error(Result.INVALID_ARGUMENT, "params must be an object");
      }
      params = (Map<String, Object>) args.get("params");
    }

    Fixtures.FixtureInfo info;
    if (hasClass) {
      Class<? extends LXFixture> fixtureClass = Fixtures.resolveFixtureClass(lx, (String) classArg);
      info = Fixtures.addFixtureByClass(lx, fixtureClass, index, label, params);
    } else {
      info = Fixtures.addFixtureByType(lx, (String) typeArg, label, params);
    }

    return Result.ok(ListFixtures.toMap(info));
  }
}
