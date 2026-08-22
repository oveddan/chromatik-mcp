package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Cameras;

public final class RemoveCamera implements LxTool {

  private final Cameras cameras;

  public RemoveCamera(Cameras cameras) {
    this.cameras = cameras;
  }

  @Override
  public String name() {
    return "remove_camera";
  }

  @Override
  public String description() {
    return "Forget a saved viewpoint. The live camera does not move — this only drops the "
        + "name from the project's saved list. Unknown name returns not_found. Not "
        + "undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("name", Schemas.string(
        "Name of a saved angle, matched exactly (case-sensitive) — see list_cameras"));
    return Schemas.object(properties, List.of("name"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Cameras.SavedCamera removed = this.cameras.remove(Args.requireString(args, "name"));
    return Result.ok(RemovalPayload.of(removed.name(), "camera"));
  }
}
