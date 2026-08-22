package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Cameras;

public final class RecallCamera implements LxTool {

  private final Cameras cameras;

  public RecallCamera(Cameras cameras) {
    this.cameras = cameras;
  }

  @Override
  public String name() {
    return "recall_camera";
  }

  @Override
  public String description() {
    return "Move the viewpoint to a saved angle (list_cameras reports the names). Shoot "
        + "successive renders of the same pattern from one recalled angle so the "
        + "differences between them are the pattern's, not the camera's. When Chromatik's "
        + "UI is running this also moves the preview a person is watching, putting them and "
        + "the render on the same viewpoint. Unknown name returns not_found. Not undoable "
        + "with Cmd-Z.";
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
    String name = Args.requireString(args, "name");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name.trim());
    payload.putAll(CameraPayload.toMap(this.cameras.recall(lx, name)));
    return Result.ok(payload);
  }
}
