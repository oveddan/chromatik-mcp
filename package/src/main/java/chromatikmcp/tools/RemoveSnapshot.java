package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.snapshot.LXGlobalSnapshot;

import chromatikmcp.domain.Resolve;
import chromatikmcp.domain.Snapshots;

public final class RemoveSnapshot implements LxTool {

  @Override
  public String name() {
    return "remove_snapshot";
  }

  @Override
  public String description() {
    return "Remove a snapshot by canonical path (as returned by add_snapshot/list_snapshots). "
        + "Snapshots are addressed by a 1-based structural path (e.g. "
        + "/lx/snapshots/snapshot/2) — remaining snapshots reindex afterwards, so held "
        + "paths can go stale; re-list before reusing one. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the snapshot, as returned by add_snapshot/list_snapshots"));
    return Schemas.object(properties, List.of("path"));
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
    Snapshots.removeSnapshot(lx, snapshot);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    payload.put("kind", "snapshot");
    return Result.ok(payload);
  }
}
