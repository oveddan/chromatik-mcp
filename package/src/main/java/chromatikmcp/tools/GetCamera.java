package chromatikmcp.tools;

import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Cameras;

public final class GetCamera implements LxTool {

  private final Cameras cameras;

  public GetCamera(Cameras cameras) {
    this.cameras = cameras;
  }

  @Override
  public String name() {
    return "get_camera";
  }

  @Override
  public String description() {
    return "Read the current 3D viewpoint — where the model is being looked at from, in "
        + "both orbit form (theta/phi/radius about a target) and absolute eye position. "
        + "Read this before set_camera to nudge the view from where it already is instead "
        + "of guessing absolute coordinates. " + CameraArgs.ORBIT_SUMMARY + " "
        + "'livePreview' says whether this is the camera a person is actually watching: "
        + "true when Chromatik's UI is up, so set_camera moves what they see; false in a "
        + "headless runtime, where the viewpoint is held by this server for get_frame "
        + "alone. When livePreview is true this reads the preview back live, so it also "
        + "reports a camera the user just moved by hand.";
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
    return Result.ok(CameraPayload.toMap(this.cameras.current(lx)));
  }
}
