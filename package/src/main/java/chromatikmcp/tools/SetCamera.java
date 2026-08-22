package chromatikmcp.tools;

import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Cameras;

public final class SetCamera implements LxTool {

  private final Cameras cameras;

  public SetCamera(Cameras cameras) {
    this.cameras = cameras;
  }

  @Override
  public String name() {
    return "set_camera";
  }

  @Override
  public String description() {
    return "Move the 3D viewpoint — the angle the model is seen from. This is what lets a "
        + "walk-in installation be judged from inside it: put the eye where a visitor "
        + "stands and aim from there, rather than reading it off an outside elevation. "
        + "Every field is optional and defaults to the current camera (get_camera), so a "
        + "single field nudges one axis. " + CameraArgs.ORBIT_SUMMARY + " When Chromatik's "
        + "UI is running this moves the 3D preview a person is watching (the response's "
        + "'livePreview' says so); headless it moves only the viewpoint this server holds. "
        + "Framing an interior angle by trial and error is slow — save_camera names the "
        + "result so later renders can be shot from the same place and actually compared. "
        + "Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.object(CameraArgs.schemaProperties(), List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!CameraArgs.present(args)) {
      return Result.error(Result.INVALID_ARGUMENT,
          "set_camera needs at least one camera field (theta, phi, radius, target, eye, "
              + "projection, fovDegrees)");
    }
    Cameras.CameraAngle base = this.cameras.current(lx).angle();
    return Result.ok(CameraPayload.toMap(this.cameras.apply(lx, CameraArgs.merge(args, base))));
  }
}
