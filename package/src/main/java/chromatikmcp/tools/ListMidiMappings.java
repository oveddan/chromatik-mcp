package chromatikmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class ListMidiMappings implements LxTool {

  @Override
  public String name() {
    return "list_midi_mappings";
  }

  @Override
  public String description() {
    return "List the parameter mappings driven by incoming MIDI. Each entry gives type "
        + "('note' or 'cc'), the 0-based MIDI channel (0-15), number (note pitch or CC "
        + "number, 0-127), a note-name for note mappings, and targetPath — the canonical "
        + "path of the mapped parameter, usable with get_parameter/set_parameter. Mappings "
        + "are addressed by their 0-based index; indices shift when a mapping is removed, "
        + "so re-list before reusing one. Only inputs with "
        + "controlEnabled (see list_midi_devices) actually apply these mappings. label is "
        + "LX's description of the mapping source (for note mappings this duplicates the "
        + "note name); targetLabel is the mapped parameter's display label.";
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
    List<Map<String, Object>> mappings = new ArrayList<>();
    for (Midi.MappingInfo m : Midi.mappings(lx)) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("index", m.index());
      entry.put("type", m.type());
      entry.put("channel", m.channel());
      entry.put("number", m.number());
      if (m.note() != null) {
        entry.put("note", m.note());
      }
      entry.put("label", m.label());
      entry.put("targetPath", m.targetPath());
      entry.put("targetLabel", m.targetLabel());
      mappings.add(entry);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("mappings", mappings);
    return Result.ok(payload);
  }
}
