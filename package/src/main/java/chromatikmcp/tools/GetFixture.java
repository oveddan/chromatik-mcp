package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Model;
import chromatikmcp.domain.Parameters;
import chromatikmcp.domain.Resolve;

public final class GetFixture implements LxTool {

  @Override
  public String name() {
    return "get_fixture";
  }

  @Override
  public String description() {
    return "One fixture's full detail: everything list_fixtures reports for it, plus "
        + "'parameters' (every parameter it owns — including type-specific ones like a "
        + "GridFixture's numRows/numColumns or an ArcFixture's degrees — settable via "
        + "set_parameter on its own path, same as any other component parameter), "
        + "'submodels' (the fixture's own child model nodes, e.g. a GridFixture's per-row and "
        + "per-column groupings — each with path/tags/size/pointIndexRange/contiguous/metaData, "
        + "same node shape as describe_model; empty when the fixture is deactivated and has no "
        + "built model — see 'modelAvailable' in list_fixtures), 'children' "
        + "(the fixture's subfixture tree — e.g. a JsonFixture's .lxf-declared 'components', "
        + "recursively — depth-limited by the 'depth' argument; each node uses the same shape "
        + "as a list_fixtures row, itself with a nested 'children' if depth allows further "
        + "recursion; 'childCount' there is the number of direct subfixtures, distinct from "
        + "'submodelCount'), and for a JsonFixture, 'jsonParameters' (the knobs its .lxf file "
        + "declares — these have no canonical path, so they carry no 'path' field here and "
        + "are NOT reachable via set_parameter; set them by name via set_fixture_params). "
        + "Subfixture paths "
        + "(e.g. '<path>/fixture/3') are addressable with get_parameter/set_parameter/"
        + "get_fixture exactly like top-level fixtures — writes to a subfixture of a "
        + "JsonFixture are rejected, since its values are computed from the .lxf and "
        + "recomputed on reload. 'depth' is silently clamped to its max (real installations "
        + "can have hundreds of subfixtures nested deep) rather than erroring — only a "
        + "negative depth is rejected.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string("Canonical path of the fixture, e.g. /lx/structure/fixture/1"));
    Map<String, Object> depthSchema = new LinkedHashMap<>();
    depthSchema.put("type", "integer");
    depthSchema.put("description", "How many levels of subfixtures to include in 'children' (default 1, "
        + "clamped to 10 max; negative is rejected). A real installation's fixture tree "
        + "can be hundreds of nodes deep and wide (e.g. ~640 subfixtures on an "
        + "Apotheneum-shaped rig), so this is capped rather than unbounded.");
    properties.put("depth", depthSchema);
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object pathArg = args.get("path");
    if (!(pathArg instanceof String path) || path.isEmpty()) {
      return Result.error(Result.INVALID_ARGUMENT, "path must be a non-empty string");
    }
    int depth = 1;
    if (args.containsKey("depth")) {
      Object depthArg = args.get("depth");
      if (!(depthArg instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
        return Result.error(Result.INVALID_ARGUMENT, "depth must be an integer");
      }
      depth = number.intValue();
      if (depth < 0) {
        return Result.error(Result.INVALID_ARGUMENT, "depth must be >= 0");
      }
      depth = Math.min(depth, 10);
    }
    LXFixture fixture = Resolve.component(lx, path, LXFixture.class);

    Map<String, Object> payload = ListFixtures.toMap(Fixtures.describeFixture(lx, fixture));

    List<Map<String, Object>> parameters = new ArrayList<>();
    for (Parameters.ParameterInfo parameter : Parameters.listFor(fixture).parameters()) {
      parameters.add(parameter.toMap());
    }
    payload.put("parameters", parameters);

    List<Map<String, Object>> submodels = new ArrayList<>();
    for (Model.NodeInfo node : Fixtures.submodels(fixture)) {
      submodels.add(submodelMap(node));
    }
    payload.put("submodels", submodels);

    if (!Fixtures.subfixturesAvailable()) {
      // Only surfaced on failure — a degraded run (reflective accessor unavailable) should
      // be visible rather than silently reporting childCount 0 / children [] for a fixture
      // that may genuinely have subfixtures. Mirrors list_fixtures' contract.
      payload.put("subfixturesAvailable", false);
    }

    if (depth > 0) {
      List<Map<String, Object>> children = new ArrayList<>();
      for (LXFixture child : Fixtures.children(fixture)) {
        children.add(fixtureNodeMap(Fixtures.describeTree(child, depth - 1)));
      }
      payload.put("children", children);
    }

    if (fixture instanceof JsonFixture jsonFixture) {
      List<Map<String, Object>> jsonParameters = new ArrayList<>();
      for (Fixtures.JsonParameterInfo parameter : Fixtures.jsonParameters(jsonFixture)) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", parameter.name());
        entry.put("label", parameter.label());
        entry.put("type", parameter.type());
        entry.put("value", parameter.value());
        jsonParameters.add(entry);
      }
      payload.put("jsonParameters", jsonParameters);
    }

    return Result.ok(payload);
  }

  private static Map<String, Object> fixtureNodeMap(Fixtures.FixtureNode node) {
    Map<String, Object> entry = ListFixtures.toMap(node.info());
    if (node.children() != null) {
      List<Map<String, Object>> children = new ArrayList<>();
      for (Fixtures.FixtureNode child : node.children()) {
        children.add(fixtureNodeMap(child));
      }
      entry.put("children", children);
    }
    return entry;
  }

  private static Map<String, Object> submodelMap(Model.NodeInfo node) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", node.path());
    entry.put("tags", node.tags());
    entry.put("size", node.size());
    if (node.firstIndex() != null) {
      entry.put("pointIndexRange", List.of(node.firstIndex(), node.lastIndex()));
      entry.put("contiguous", node.contiguous());
    }
    if (!node.metaData().isEmpty()) {
      entry.put("metaData", node.metaData());
    }
    return entry;
  }
}
