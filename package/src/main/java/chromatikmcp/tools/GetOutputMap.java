package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.structure.LXFixture;

import chromatikmcp.domain.Fixtures;
import chromatikmcp.domain.Resolve;

/**
 * Read-only diagnostic: which LED index goes to which DMX/Art-Net universe & channel, per
 * fixture, and whether anything collides.
 */
public final class GetOutputMap implements LxTool {

  @Override
  public String name() {
    return "get_output_map";
  }

  @Override
  public String description() {
    return "The output wiring beneath the fixture tree: for each fixture, its declared "
        + "protocol/host/port/universe/channel/byteOrder plus a DERIVED channel footprint "
        + "('numChannels' — own point count times bytes-per-pixel) and an "
        + "'estimatedUniverseSpan' [startUniverse, endUniverse] for universe-based protocols "
        + "(ARTNET/SACN/KINET) computed by rolling that footprint across universes the same "
        + "way LX itself overflows (512 DMX channels per universe for ARTNET/SACN/KINET). "
        + "'pointIndexRange' indexes the same global point buffer describe_model/get_frame use. "
        + "IMPORTANT: this map is DECLARED/DERIVED, NOT LX's resolved per-packet output "
        + "allocation — LX keeps that privately (LXStructureOutput's generatedOutputs/Packet "
        + "have no public accessor) — and the span estimate assumes one contiguous segment per "
        + "fixture, ignoring serpentine wiring, segment stride, and cross-fixture packet "
        + "packing, so it can diverge from LX's actual allocation on complex rigs. OPC/DDP "
        + "fixtures get 'estimatedUniverseSpan: null' (those protocols use a data-length model, "
        + "not universes). A fixture whose wiring is declared inside its .lxf file (e.g. a real "
        + "installation's JsonFixture) reports 'directOutputCount' (its outputsDirect size) and "
        + "an 'outputsNote' instead of a universe/channel estimate — LX exposes no public "
        + "accessor for a .lxf-declared output's resolved universe, so none is fabricated. "
        + "'outputError' is LX's own collision report (lx.structure.outputError) — non-empty "
        + "means LX itself detected an overlap; trust it over the estimate. Pairs with "
        + "set_fixture_params: set artNetUniverse/dmxChannel/etc., then re-call this to check "
        + "the resulting footprint. 'path' optional: map one fixture's subtree; omitted maps "
        + "every top-level fixture.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of a fixture to map (with its subfixture subtree); omit to map every "
            + "top-level fixture"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXFixture root = null;
    Object pathArg = args.get("path");
    if (pathArg != null) {
      if (!(pathArg instanceof String path) || path.isEmpty()) {
        return Result.error(Result.INVALID_ARGUMENT, "path must be a non-empty string");
      }
      root = Resolve.component(lx, path, LXFixture.class);
    }

    Fixtures.OutputMapSnapshot snapshot = Fixtures.outputMap(lx, root);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("outputError", snapshot.outputError());
    payload.put("note", snapshot.note());
    List<Map<String, Object>> fixtures = new ArrayList<>();
    for (Fixtures.OutputMapEntry entry : snapshot.fixtures()) {
      fixtures.add(entryMap(entry));
    }
    payload.put("fixtures", fixtures);
    return Result.ok(payload);
  }

  private static Map<String, Object> entryMap(Fixtures.OutputMapEntry entry) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("path", entry.path());
    map.put("type", entry.type());
    if (entry.firstIndex() != null) {
      map.put("pointIndexRange", List.of(entry.firstIndex(), entry.lastIndex()));
    }
    if (entry.protocol() != null) {
      map.put("protocol", entry.protocol());
    }
    if (entry.host() != null) {
      map.put("host", entry.host());
      map.put("port", entry.port());
      map.put("universe", entry.universe());
      map.put("channel", entry.channel());
      map.put("byteOrder", entry.byteOrder());
      map.put("numChannels", entry.numChannels());
      map.put("estimatedUniverseSpan", entry.estimatedUniverseSpan());
    }
    if (entry.directOutputCount() != null) {
      map.put("directOutputCount", entry.directOutputCount());
      map.put("outputsNote", entry.outputsNote());
    }
    map.put("childCount", entry.childCount());
    if (entry.children() != null) {
      List<Map<String, Object>> children = new ArrayList<>();
      for (Fixtures.OutputMapEntry child : entry.children()) {
        children.add(entryMap(child));
      }
      map.put("children", children);
    }
    return map;
  }
}
