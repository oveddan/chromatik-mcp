package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.modulator.LXModulator;

import chromatikmcp.domain.Modulators;

public final class MoveModulator implements LxTool {

  @Override
  public String name() {
    return "move_modulator";
  }

  @Override
  public String description() {
    return "Move a modulator to a new 0-based index within its global or device-local "
        + "modulation engine. Index 0 is the first (top) entry. Moving shifts the 1-based "
        + "canonical paths of the moved modulator and any sibling it crosses; re-list "
        + "modulations rather than reusing cached paths. The response's oscChanges array "
        + "reports exactly which component canonical paths changed (componentId, before, "
        + "after). Label-based OSC addresses do not change. Returns invalid_argument if the "
        + "index is out of range. Undoable in Chromatik with Cmd-Z, which a human can trigger "
        + "outside this session's control; re-list after any move if undo is possible.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the modulator to move, e.g. /lx/modulation/modulator/1"));
    properties.put("index", Schemas.integer(
        "0-based destination index within the modulation engine", Integer.MIN_VALUE, Integer.MAX_VALUE));
    return Schemas.object(properties, List.of("path", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    int index = Args.requireInt(args, "index");
    Modulators.ModulatorMoveResult result = Modulators.moveModulator(lx, path, index);
    LXModulator modulator = result.modulator();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", modulator.getCanonicalPath());
    payload.put("id", modulator.getId());
    payload.put("label", modulator.getLabel());
    payload.put("index", modulator.getIndex());
    payload.put("oscChanges", OscChanges.payload(result.oscChanges()));
    return Result.ok(payload);
  }
}
