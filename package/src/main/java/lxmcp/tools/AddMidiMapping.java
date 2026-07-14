package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

import lxmcp.domain.MidiMappings;
import lxmcp.domain.Resolve;

public final class AddMidiMapping implements LxTool {

  @Override
  public String name() {
    return "add_midi_mapping";
  }

  @Override
  public String description() {
    return "Map an incoming MIDI note or control-change message onto a parameter. "
        + "channel is 1-16 (MIDI convention); number is the note pitch or CC number, "
        + "0-127. Rejects unmappable parameters (LXParameter.isMappable() == false, e.g. "
        + "internal engine/MIDI/OSC config knobs) and out-of-range channel/number. "
        + "Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("target", Schemas.string(
        "Canonical path of the parameter to map onto"));
    properties.put("type", Map.of("type", "string", "enum", List.of("note", "cc"),
        "description", "\"note\" for a MIDI note on/off, \"cc\" for a control change"));
    properties.put("channel", Map.of("type", "integer",
        "description", "MIDI channel, 1-16"));
    properties.put("number", Map.of("type", "integer",
        "description", "Note pitch or CC number, 0-127"));
    return Schemas.object(properties, List.of("target", "type", "channel", "number"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("target") instanceof String targetPath)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: target");
    }
    if (!(args.get("type") instanceof String typeArg)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: type");
    }
    MidiMappings.MessageType type;
    switch (typeArg) {
      case "note" -> type = MidiMappings.MessageType.NOTE;
      case "cc" -> type = MidiMappings.MessageType.CC;
      default -> {
        return Result.error(Result.INVALID_ARGUMENT, "type must be \"note\" or \"cc\" (got: " + typeArg + ")");
      }
    }
    if (!(args.get("channel") instanceof Number channelArg)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required integer argument: channel");
    }
    if (!(args.get("number") instanceof Number numberArg)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required integer argument: number");
    }
    LXParameter target = Resolve.parameter(lx, targetPath);
    if (!(target instanceof LXNormalizedParameter normalizedTarget)) {
      return Result.error(Result.INVALID_ARGUMENT, "Target " + targetPath
          + " (" + target.getClass().getSimpleName() + ") is not a normalized parameter "
          + "and cannot be MIDI-mapped");
    }
    LXMidiMapping mapping = MidiMappings.addMapping(
        lx, type, channelArg.intValue(), numberArg.intValue(), normalizedTarget);
    int index = lx.engine.midi.mappings.indexOf(mapping);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("index", index);
    payload.put("type", typeArg);
    payload.put("channel", channelArg.intValue());
    payload.put("number", numberArg.intValue());
    payload.put("targetPath", target.getCanonicalPath());
    return Result.ok(payload);
  }
}
