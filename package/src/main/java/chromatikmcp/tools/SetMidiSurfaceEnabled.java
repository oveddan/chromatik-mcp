package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Midi;

public final class SetMidiSurfaceEnabled implements LxTool {

  @Override
  public String name() {
    return "set_midi_surface_enabled";
  }

  @Override
  public String description() {
    return "Enable or disable a control surface by its 0-based index into "
        + "list_midi_surfaces. Returns the updated surface in list_midi_surfaces' shape. "
        + "Not undoable — LX has no undo command for surface enablement.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("index", Schemas.integer(
        "0-based index of the surface, as returned by list_midi_surfaces",
        Integer.MIN_VALUE, Integer.MAX_VALUE));
    properties.put("enabled", Schemas.bool("Whether the surface should be enabled"));
    return Schemas.object(properties, List.of("index", "enabled"));
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
    if (!(args.get("enabled") instanceof Boolean enabled)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required boolean argument: enabled");
    }
    Midi.SurfaceInfo info = Midi.setSurfaceEnabled(lx, n.intValue(), enabled);
    return Result.ok(ListMidiSurfaces.toMap(info));
  }
}
