package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Resolve;

public final class DuplicateFixture implements LxTool {

  @Override
  public String name() {
    return "duplicate_fixture";
  }

  @Override
  public String description() {
    return "Clone a fixture — geometry, output protocol wiring, and (for a JsonFixture) its "
        + ".lxf-declared parameter values all copy over — in one call, matching the UI's "
        + "duplicate action. The clone gets a fresh component id and its output-enabled flag "
        + "is reset to off (never silently start transmitting a duplicate). 'index' "
        + "defaults to right after the source fixture; explicit values are clamped into "
        + "[0, fixtureCount]. Undoable with Cmd-Z. Every fixture's path is POSITIONAL "
        + "(/lx/structure/fixture/N, 1-indexed) and shifts after this call — re-list "
        + "(list_fixtures) rather than reuse a held path. Rejected when the structure is in "
        + "static-model mode.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the fixture to duplicate, e.g. /lx/structure/fixture/1"));
    properties.put("index", Map.of(
        "type", "integer",
        "description", "0-based insert position for the clone, clamped into "
            + "[0, fixtureCount]; omit to insert right after the source fixture."));
    return Schemas.object(properties, List.of("path"));
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
    Integer index = null;
    if (args.containsKey("index")) {
      Object indexArg = args.get("index");
      if (!(indexArg instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
        return Result.error(Result.INVALID_ARGUMENT, "index must be an integer");
      }
      index = number.intValue();
    }
    LXFixture source = Resolve.component(lx, path, LXFixture.class);
    Fixtures.FixtureInfo info = Fixtures.duplicateFixture(lx, source, index);
    return Result.ok(ListFixtures.toMap(info));
  }
}
