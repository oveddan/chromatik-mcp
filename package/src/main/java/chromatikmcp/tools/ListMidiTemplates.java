package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class ListMidiTemplates implements LxTool {

  @Override
  public String name() {
    return "list_midi_templates";
  }

  @Override
  public String description() {
    return "List the MIDI templates instantiated in this project. Templates expose named "
        + "hardware controls as ordinary parameters at paths such as "
        + "/lx/midi/template/1/knob-A1, which can be inspected with list_parameters and "
        + "used with wire_modulator or wire_trigger. Each entry includes its canonical "
        + "path, registered class, expected device name, selected source/output, and "
        + "connection state. The 0-based index and 1-based path may shift when templates "
        + "are removed or reordered, so re-list before reusing them.";
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
    List<Map<String, Object>> templates = new ArrayList<>();
    for (Midi.TemplateInfo template : Midi.templates(lx)) {
      templates.add(toMap(template));
    }
    return Result.ok(Map.of("templates", templates));
  }

  static Map<String, Object> toMap(Midi.TemplateInfo template) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("index", template.index());
    entry.put("path", template.path());
    entry.put("id", template.id());
    entry.put("name", template.name());
    entry.put("label", template.label());
    Payloads.putIfPresent(entry, "deviceName", template.deviceName());
    entry.put("class", template.className());
    entry.put("connected", template.connected());
    Payloads.putIfPresent(entry, "inputName", template.inputName());
    Payloads.putIfPresent(entry, "outputName", template.outputName());
    return entry;
  }
}
