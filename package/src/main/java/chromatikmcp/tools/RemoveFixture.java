package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Resolve;

public final class RemoveFixture implements LxTool {

  @Override
  public String name() {
    return "remove_fixture";
  }

  @Override
  public String description() {
    return "Remove a fixture by its canonical path (as returned by list_fixtures/add_fixture). "
        + "Undoable with Cmd-Z. Every remaining fixture's path is POSITIONAL "
        + "(/lx/structure/fixture/N, 1-indexed) and shifts after this call — re-list "
        + "(list_fixtures) rather than reuse a held path. Rejected when the structure is in "
        + "static-model mode.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("path", Schemas.string(
            "Canonical path of the fixture to remove, e.g. /lx/structure/fixture/1")),
        List.of("path"));
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
    LXFixture fixture = Resolve.component(lx, path, LXFixture.class);
    Fixtures.removeFixture(lx, fixture);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    payload.put("kind", "fixture");
    return Result.ok(payload);
  }
}
