package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.MidiMappings;

public final class RemoveMidiMapping implements LxTool {

  @Override
  public String name() {
    return "remove_midi_mapping";
  }

  @Override
  public String description() {
    return "Remove a MIDI mapping by its list index, as returned by list_midi_mappings or "
        + "add_midi_mapping. Mappings have no canonical path in LX, so this is the only "
        + "addressing scheme available. Removing an earlier mapping shifts the indices of "
        + "later ones — re-list rather than reusing a cached index. Undoable in Chromatik "
        + "with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("index", Map.of("type", "integer",
            "description", "0-based list index, as returned by list_midi_mappings")),
        List.of("index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("index") instanceof Number n)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required integer argument: index");
    }
    int index = n.intValue();
    MidiMappings.removeMapping(lx, index);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", index);
    return Result.ok(payload);
  }
}
