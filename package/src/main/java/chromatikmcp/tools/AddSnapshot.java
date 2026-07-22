package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.snapshot.LXGlobalSnapshot;

import chromatikmcp.domain.Snapshots;

public final class AddSnapshot implements LxTool {

  @Override
  public String name() {
    return "add_snapshot";
  }

  @Override
  public String description() {
    return "Capture the current mixer/pattern/effect/modulation state as a new snapshot, "
        + "appended to the end of the list. The optional label overrides LX's default "
        + "'Snapshot-N' name. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("label", Schemas.string(
        "Optional label for the new snapshot"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object labelArg = args.get("label");
    if (labelArg != null && !(labelArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "label must be a string");
    }
    LXGlobalSnapshot snapshot = Snapshots.addSnapshot(lx, (String) labelArg);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", snapshot.getCanonicalPath());
    payload.put("id", snapshot.getId());
    payload.put("label", snapshot.getLabel());
    payload.put("transitionTimeSecs", snapshot.transitionTimeSecs.getValue());
    return Result.ok(payload);
  }
}
