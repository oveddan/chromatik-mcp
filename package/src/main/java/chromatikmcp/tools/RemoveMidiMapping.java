package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class RemoveMidiMapping implements LxTool {

  @Override
  public String name() {
    return "remove_midi_mapping";
  }

  @Override
  public String description() {
    return "Remove a MIDI mapping by its 0-based index into list_midi_mappings. Returns the "
        + "removed mapping's summary. Remaining mappings reindex afterwards, so held indices "
        + "go stale — re-list before reusing one. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("index", Schemas.integer(
        "0-based index of the mapping to remove, as returned by list_midi_mappings",
        Integer.MIN_VALUE, Integer.MAX_VALUE));
    return Schemas.object(properties, List.of("index"));
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
    Midi.MappingInfo removed = Midi.removeMapping(lx, n.intValue());
    return Result.ok(AddMidiMapping.toMap(removed));
  }
}
