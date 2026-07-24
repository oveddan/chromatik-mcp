package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class ListMidiDevices implements LxTool {

  @Override
  public String name() {
    return "list_midi_devices";
  }

  @Override
  public String description() {
    return "List the MIDI input and output ports LX has discovered. Each input carries "
        + "three independent routing flags: channelEnabled (notes/CCs forwarded to channel "
        + "and modulator devices), controlEnabled (events feed the control-mapping layer — "
        + "see list_midi_mappings), and syncEnabled (this port's MIDI clock drives the "
        + "engine tempo, effective only when get_tempo reports clockSource MIDI). enabled is "
        + "the union of those three. connected starts true and flips false only if the device "
        + "disconnects mid-session; ports remembered from the project file whose hardware is "
        + "absent are not listed at all. Ports are addressed by their 0-based index (they "
        + "carry no canonical path); indices shift as devices connect or disconnect, so "
        + "re-list before reusing one.";
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
    Midi.DevicesInfo info = Midi.devices(lx);
    List<Map<String, Object>> inputs = new ArrayList<>();
    for (Midi.InputInfo in : info.inputs()) {
      inputs.add(toMap(in));
    }
    List<Map<String, Object>> outputs = new ArrayList<>();
    for (Midi.OutputInfo out : info.outputs()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("index", out.index());
      entry.put("name", out.name());
      entry.put("description", out.description());
      entry.put("connected", out.connected());
      entry.put("enabled", out.enabled());
      outputs.add(entry);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("inputs", inputs);
    payload.put("outputs", outputs);
    return Result.ok(payload);
  }

  static Map<String, Object> toMap(Midi.InputInfo in) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("index", in.index());
    entry.put("name", in.name());
    entry.put("description", in.description());
    entry.put("connected", in.connected());
    entry.put("enabled", in.enabled());
    entry.put("channelEnabled", in.channelEnabled());
    entry.put("controlEnabled", in.controlEnabled());
    entry.put("syncEnabled", in.syncEnabled());
    return entry;
  }
}
