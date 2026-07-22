package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Resolve;

public final class MoveFixture implements LxTool {

  @Override
  public String name() {
    return "move_fixture";
  }

  @Override
  public String description() {
    return "Reposition a fixture within lx.structure.fixtures (0-based 'index', clamped "
        + "into [0, fixtureCount - 1]). Undoable with Cmd-Z. Every fixture's path is "
        + "POSITIONAL (/lx/structure/fixture/N, 1-indexed) and shifts for this fixture and "
        + "any it moved past — re-list (list_fixtures) rather than reuse a held path. "
        + "Rejected when the structure is in static-model mode.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the fixture to move, e.g. /lx/structure/fixture/2"));
    properties.put("index", Map.of(
        "type", "integer",
        "description", "0-based target position in lx.structure.fixtures, clamped into "
            + "[0, fixtureCount - 1]"));
    return Schemas.object(properties, List.of("path", "index"));
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
    Object indexArg = args.get("index");
    if (!(indexArg instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
      return Result.error(Result.INVALID_ARGUMENT, "Required integer argument: index");
    }
    LXFixture fixture = Resolve.component(lx, path, LXFixture.class);
    Fixtures.FixtureInfo info = Fixtures.moveFixture(lx, fixture, number.intValue());
    return Result.ok(ListFixtures.toMap(info));
  }
}
