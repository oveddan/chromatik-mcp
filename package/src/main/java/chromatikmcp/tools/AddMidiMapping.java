package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class AddMidiMapping implements LxTool {

  @Override
  public String name() {
    return "add_midi_mapping";
  }

  @Override
  public String description() {
    return "Add a MIDI mapping: incoming note-on or control-change messages on a channel "
        + "drive a parameter, resolved by its canonical LX path (see list_parameters). type "
        + "is 'note' (number is the pitch, 0-127) or 'cc' (number is the CC number, 0-127); "
        + "channel is 0-based (0-15). The mapping fires on channel+pitch/cc identity, not a "
        + "specific velocity/value — the actual incoming velocity/value still reaches the "
        + "parameter at runtime. Only parameters that support MIDI mapping (most "
        + "numeric/bounded/toggle/discrete ones) can be targeted; aggregate parameters "
        + "(color, MIDI filter) are rejected — map their component paths instead. Returns "
        + "the created mapping in list_midi_mappings' shape, including its 0-based index; "
        + "that index shifts if other mappings are later removed, so re-list before reusing "
        + "it. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("type", Schemas.enumString(
        "Mapping type: 'note' (note-on) or 'cc' (control change)", List.of("note", "cc")));
    properties.put("channel", Schemas.integer("0-based MIDI channel (0-15)", 0, 15));
    properties.put("number", Schemas.integer(
        "Note pitch or CC number, 0-127 depending on type", 0, 127));
    properties.put("targetPath", Schemas.string(
        "Canonical LX path of the parameter to map, as returned by the list/get tools"));
    return Schemas.object(properties, List.of("type", "channel", "number", "targetPath"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("type") instanceof String type) || !(type.equals("note") || type.equals("cc"))) {
      return Result.error(Result.INVALID_ARGUMENT, "type must be 'note' or 'cc'");
    }
    int channel = Args.requireInt(args, "channel");
    int number = Args.requireInt(args, "number");
    String targetPath = Args.requireString(args, "targetPath");
    Midi.MappingInfo mapping = Midi.addMapping(lx, type, channel, number, targetPath);
    return Result.ok(toMap(mapping));
  }

  static Map<String, Object> toMap(Midi.MappingInfo m) {
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
    return entry;
  }
}
