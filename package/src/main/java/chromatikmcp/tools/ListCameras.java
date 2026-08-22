package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Cameras;

public final class ListCameras implements LxTool {

  private final Cameras cameras;

  public ListCameras(Cameras cameras) {
    this.cameras = cameras;
  }

  @Override
  public String name() {
    return "list_cameras";
  }

  @Override
  public String description() {
    return "The viewpoints saved in this project by save_camera, in the order they were "
        + "first named, each with the same angle fields get_camera reports. Check here "
        + "before framing an interior angle by hand — someone may already have named the "
        + "one you want. Empty on a project that has never saved one.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    List<Map<String, Object>> entries =
        this.cameras.list().stream().map(CameraPayload::toMap).toList();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("cameras", entries);
    return Result.ok(payload);
  }
}
