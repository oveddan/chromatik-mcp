package lxmcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.MidiMappings;

public final class ListMidiMappings implements LxTool {

  @Override
  public String name() {
    return "list_midi_mappings";
  }

  @Override
  public String description() {
    return "List live MIDI mappings (note/CC to parameter wirings). Mappings have no "
        + "canonical path — they are addressed by list index, which shifts when an earlier "
        + "mapping is removed; re-list rather than reusing a cached index.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(Map.of(), List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    List<Map<String, Object>> mappings = new ArrayList<>();
    for (MidiMappings.MappingInfo info : MidiMappings.list(lx)) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("index", info.index());
      entry.put("type", info.type() == MidiMappings.MessageType.NOTE ? "note" : "cc");
      entry.put("channel", info.channel());
      entry.put("number", info.number());
      entry.put("targetPath", info.targetPath());
      mappings.add(entry);
    }
    return Result.ok(Map.of("mappings", mappings));
  }
}
