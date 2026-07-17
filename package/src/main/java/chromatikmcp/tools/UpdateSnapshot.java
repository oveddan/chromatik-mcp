package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.snapshot.LXGlobalSnapshot;

import chromatikmcp.domain.Resolve;
import chromatikmcp.domain.Snapshots;

public final class UpdateSnapshot implements LxTool {

  @Override
  public String name() {
    return "update_snapshot";
  }

  @Override
  public String description() {
    return "Recapture the current mixer/pattern/effect/modulation state into an existing "
        + "snapshot, overwriting its previously saved values. Undoable in Chromatik with "
        + "Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(
        Map.of("path", Schemas.string(
            "Canonical path of the snapshot to update, as returned by add_snapshot/list_snapshots")),
        List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("path") instanceof String path)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    LXGlobalSnapshot snapshot = Resolve.component(lx, path, LXGlobalSnapshot.class);
    Snapshots.update(lx, snapshot);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("updated", path);
    return Result.ok(payload);
  }
}
