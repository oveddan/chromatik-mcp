package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class SetMidiInput implements LxTool {

  @Override
  public String name() {
    return "set_midi_input";
  }

  @Override
  public String description() {
    return "Set one or more of a MIDI input's routing flags by its 0-based index into "
        + "list_midi_devices' inputs list: channelEnabled (forward notes/CCs to channel and "
        + "modulator devices), controlEnabled (feed the control-mapping layer — see "
        + "list_midi_mappings), syncEnabled (this port's MIDI clock drives the engine tempo). "
        + "At least one flag must be provided; flags left unset are unchanged. enabled is a "
        + "derived union of the three and cannot be set directly. Returns the updated input "
        + "in list_midi_devices' shape. Not undoable — LX has no undo command for these "
        + "flags.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("index", Schemas.integer(
        "0-based index of the input, as returned by list_midi_devices",
        Integer.MIN_VALUE, Integer.MAX_VALUE));
    properties.put("channelEnabled", Schemas.bool(
        "Forward notes/CCs from this input to channel and modulator devices"));
    properties.put("controlEnabled", Schemas.bool(
        "Feed events from this input to the control-mapping layer"));
    properties.put("syncEnabled", Schemas.bool(
        "Let this input's MIDI clock drive the engine tempo"));
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
    Boolean channelEnabled = boolArg(args, "channelEnabled");
    Boolean controlEnabled = boolArg(args, "controlEnabled");
    Boolean syncEnabled = boolArg(args, "syncEnabled");
    if (channelEnabled == null && controlEnabled == null && syncEnabled == null) {
      return Result.error(Result.INVALID_ARGUMENT,
          "At least one of channelEnabled, controlEnabled, syncEnabled is required");
    }
    Midi.InputInfo info =
        Midi.setInputFlags(lx, n.intValue(), channelEnabled, controlEnabled, syncEnabled);
    return Result.ok(ListMidiDevices.toMap(info));
  }

  private static Boolean boolArg(Map<String, Object> args, String key) {
    return (args.get(key) instanceof Boolean b) ? b : null;
  }
}
