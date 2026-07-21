package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Fixtures;

public final class ReloadFixtures implements LxTool {

  @Override
  public String name() {
    return "reload_fixtures";
  }

  @Override
  public String description() {
    return "Pick up .lxf fixture files edited on disk with your own file tools — nothing "
        + "watches the Fixtures folder, so a .lxf edit is otherwise invisible until this "
        + "is called. Two steps: re-walks the Fixtures folder to refresh the available "
        + "fixture type list ('jsonTypes'/'errors'), then reloads every instantiated "
        + "top-level JsonFixture from its .lxf and regenerates the model exactly once — "
        + "the only batched regeneration path in LX, so this is cheaper than N individual "
        + "set_fixture_params calls. Also the only way to pick up a changed fixture *type* "
        + "on a live fixture (changing its type otherwise has no effect, since loading "
        + "only happens once). A JSON parameter's value survives the reload only if a "
        + "parameter of the same name still exists in the new .lxf; otherwise it reverts "
        + "to the file's declared default. Not undoable. Returns the refreshed type list "
        + "plus every fixture's error/errorMessage/warnings after the reload, so failures "
        + "in the edited file are visible immediately.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Fixtures.ReloadResult result = Fixtures.reload(lx);

    List<Map<String, Object>> errors = new ArrayList<>();
    for (Fixtures.JsonTypeError error : result.errors()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", error.path());
      entry.put("type", error.type());
      entry.put("exception", error.exception());
      errors.add(entry);
    }

    List<Map<String, Object>> fixtures = new ArrayList<>();
    for (Fixtures.FixtureInfo fixture : result.fixtures()) {
      fixtures.add(ListFixtures.toMap(fixture));
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("jsonTypes", result.jsonTypes());
    payload.put("errors", errors);
    payload.put("fixtures", fixtures);
    return Result.ok(payload);
  }
}
