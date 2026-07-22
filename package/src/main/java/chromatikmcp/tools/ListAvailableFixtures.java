package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Fixtures;

public final class ListAvailableFixtures implements LxTool {

  @Override
  public String name() {
    return "list_available_fixtures";
  }

  @Override
  public String description() {
    return "What add_fixture can instantiate: 'classes' (built-in fixture types — pass the "
        + "simple name, e.g. 'GridFixture', or the full class name, as add_fixture's 'class' "
        + "argument) and 'jsonTypes' (fixtures loaded from a .lxf file in the Fixtures "
        + "folder — pass the type string, e.g. 'MyRig/Cube', as add_fixture's 'type' "
        + "argument; 'isVisible' false means the .lxf declares itself hidden from the add "
        + "menu, e.g. a subfixture-only helper type). 'errors' lists any .lxf that failed to "
        + "parse (syntax/I-O error) — its type is not addable until fixed. "
        + "'fixturesDirectory' is the absolute path .lxf files live in — write a new one "
        + "there with your own file tools, then call this again (or reload_fixtures) to "
        + "pick it up.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Fixtures.AvailableFixtures available = Fixtures.describeAvailable(lx);

    List<Map<String, Object>> jsonTypes = new ArrayList<>();
    for (Fixtures.JsonTypeInfo jsonType : available.jsonTypes()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("type", jsonType.type());
      entry.put("isVisible", jsonType.isVisible());
      jsonTypes.add(entry);
    }

    List<Map<String, Object>> errors = new ArrayList<>();
    for (Fixtures.JsonTypeError error : available.errors()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("path", error.path());
      entry.put("type", error.type());
      if (error.exception() != null) {
        entry.put("exception", error.exception());
      }
      errors.add(entry);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("classes", available.classes());
    payload.put("jsonTypes", jsonTypes);
    payload.put("errors", errors);
    payload.put("fixturesDirectory", available.fixturesDirectory());
    return Result.ok(payload);
  }
}
