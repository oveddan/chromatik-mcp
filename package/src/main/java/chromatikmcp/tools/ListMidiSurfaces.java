package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class ListMidiSurfaces implements LxTool {

  @Override
  public String name() {
    return "list_midi_surfaces";
  }

  @Override
  public String description() {
    return "List the instantiated MIDI control surfaces (e.g. an APC40, a MidiFighterTwister) "
        + "— a surface is a two-way hardware controller LX drives with a dedicated protocol, "
        + "distinct from the ad-hoc parameter mappings in list_midi_mappings. Each entry "
        + "gives the surface name, the deviceName it binds to, enabled (actively driving the "
        + "hardware) and connected (device present). Surfaces are addressed by their 0-based "
        + "index. A registered surface only appears here once its device has been seen; "
        + "surfaces LX knows how to drive but hasn't instantiated are not listed.";
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
    List<Map<String, Object>> surfaces = new ArrayList<>();
    for (Midi.SurfaceInfo s : Midi.surfaces(lx)) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("index", s.index());
      entry.put("name", s.name());
      entry.put("deviceName", s.deviceName());
      entry.put("class", s.className());
      entry.put("enabled", s.enabled());
      entry.put("connected", s.connected());
      entry.put("inputName", s.inputName());
      if (s.outputName() != null) {
        entry.put("outputName", s.outputName());
      }
      surfaces.add(entry);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("surfaces", surfaces);
    return Result.ok(payload);
  }
}
