package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
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
        + "same node shape as describe_model), and for a "
        + "JsonFixture, 'jsonParameters' (the knobs its .lxf file declares, each settable via "
        + "set_parameter on '<path>/<name>').";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string("Canonical path of the fixture, e.g. /lx/structure/fixture/1"));
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
    LXFixture fixture = Resolve.component(lx, path, LXFixture.class);

    Map<String, Object> payload = ListFixtures.toMap(Fixtures.describeFixture(fixture));

    List<Map<String, Object>> parameters = new ArrayList<>();
    for (Parameters.ParameterInfo parameter : Parameters.listFor(lx, path).parameters()) {
      parameters.add(parameter.toMap());
    }
    payload.put("parameters", parameters);

    List<Map<String, Object>> submodels = new ArrayList<>();
    LXModel model = fixture.getModel();
    for (LXModel child : model.children) {
      submodels.add(submodelMap(Model.describeNode(child, 0)));
    }
    payload.put("submodels", submodels);

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
