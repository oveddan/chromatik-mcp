package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Fixtures;

public final class ListFixtures implements LxTool {

  @Override
  public String name() {
    return "list_fixtures";
  }

  @Override
  public String description() {
    return "The fixture layer: the physical wiring beneath the model tree describe_model "
        + "reports — each fixture's geometry transform, output protocol wiring, and (for a "
        + "JsonFixture, loaded from a .lxf file) its load status. 'pointIndexRange' ([firstIndex, "
        + "lastIndex]) indexes the same global color buffer get_frame and describe_model report "
        + "against. Every fixture parameter is settable via set_parameter on '<path>/<param>' — "
        + "e.g. '<path>/artNetUniverse', '<path>/x' — this is the primary way to configure a "
        + "fixture's wiring and placement once it exists. Top-level 'outputError' reports "
        + "universe/channel collisions LX detected between fixtures' output segments (empty when "
        + "clean). 'output' is present only for a protocol-driven fixture (protocol 'NONE' when "
        + "no output is configured); a JsonFixture's outputs are declared inside its .lxf file "
        + "instead, so it has no 'output' key — see 'fixturePath'/'error'/'warnings' there "
        + "instead. A fixture can itself contain subfixtures (e.g. a JsonFixture's .lxf "
        + "'components', recursively) — 'childCount' is the number of those subfixtures, while "
        + "'submodelCount' is the unrelated number of model-tree groupings the fixture's own "
        + "geometry splits into (e.g. a GridFixture's per-row/per-column submodels; it has 0 "
        + "subfixtures but several submodels). A deactivated fixture (see 'deactivate') has no "
        + "built model until it is reactivated and the structure regenerates — for such a "
        + "fixture 'modelAvailable' is reported as false (omitted, meaning true, otherwise), "
        + "'tags' falls back to the .lxf-declared subset only, and 'submodelCount' is 0. "
        + "Subfixture paths (e.g. "
        + "/lx/structure/fixture/1/fixture/3) are addressable with get_parameter/set_parameter "
        + "exactly like top-level fixtures — writes to a subfixture of a JsonFixture are "
        + "rejected, since its values are computed from the .lxf and recomputed on reload. Use "
        + "get_fixture on a single fixture's path for its full parameter list, submodels, and "
        + "subfixture tree (with a depth limit).";
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
    Fixtures.FixturesSnapshot snapshot = Fixtures.describe(lx);

    List<Map<String, Object>> fixtures = new ArrayList<>();
    for (Fixtures.FixtureInfo fixture : snapshot.fixtures()) {
      fixtures.add(toMap(fixture));
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("modelName", snapshot.modelName());
    payload.put("isStatic", snapshot.isStatic());
    payload.put("totalPoints", snapshot.totalPoints());
    payload.put("outputError", snapshot.outputError());
    if (!snapshot.subfixturesAvailable()) {
      // Only surfaced on failure — a degraded run (reflective accessor unavailable) should
      // be visible rather than silently reporting childCount 0 for every fixture.
      payload.put("subfixturesAvailable", false);
    }
    payload.put("fixtures", fixtures);
    return Result.ok(payload);
  }

  static Map<String, Object> toMap(Fixtures.FixtureInfo fixture) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("path", fixture.path());
    entry.put("id", fixture.id());
    entry.put("index", fixture.index());
    entry.put("label", fixture.label());
    entry.put("type", fixture.type());
    entry.put("size", fixture.size());
    if (fixture.firstIndex() != null) {
      entry.put("pointIndexRange", List.of(fixture.firstIndex(), fixture.lastIndex()));
    }
    entry.put("enabled", fixture.enabled());
    entry.put("deactivate", fixture.deactivate());
    entry.put("brightness", fixture.brightness());
    entry.put("tags", fixture.tags());
    entry.put("childCount", fixture.childCount());
    entry.put("submodelCount", fixture.submodelCount());
    if (!fixture.modelAvailable()) {
      // Only surfaced when false — a deactivated fixture has no built model until it is
      // reactivated and regenerated; this distinguishes "no submodels" from "geometry not
      // currently built" rather than silently reporting 0/[] either way.
      entry.put("modelAvailable", false);
    }

    Map<String, Object> transform = new LinkedHashMap<>();
    transform.put("x", fixture.transform().x());
    transform.put("y", fixture.transform().y());
    transform.put("z", fixture.transform().z());
    transform.put("yaw", fixture.transform().yaw());
    transform.put("pitch", fixture.transform().pitch());
    transform.put("roll", fixture.transform().roll());
    transform.put("scale", fixture.transform().scale());
    entry.put("transform", transform);

    if (fixture.output() != null) {
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("protocol", fixture.output().protocol());
      output.put("host", fixture.output().host());
      output.put("port", fixture.output().port());
      output.put("universe", fixture.output().universe());
      output.put("channel", fixture.output().channel());
      output.put("byteOrder", fixture.output().byteOrder());
      output.put("reverse", fixture.output().reverse());
      entry.put("output", output);
    }

    if (fixture.json() != null) {
      entry.put("fixturePath", fixture.json().fixturePath());
      if (fixture.json().error()) {
        entry.put("error", true);
        entry.put("errorMessage", fixture.json().errorMessage());
      }
      if (!fixture.json().warnings().isEmpty()) {
        entry.put("warnings", fixture.json().warnings());
      }
    }
    return entry;
  }
}
