package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Cameras;
import chromatikmcp.engine.EngineExecutor;

/** Smooth camera move whose client-visible wait happens off the LX engine thread. */
public final class AnimateCamera implements LxTool {

  /** One second of headroom under the seam's only 30-second tool-call timeout. */
  static final int MAX_DURATION_MS = (int) EngineExecutor.DEFAULT_TIMEOUT_MS - 1_000;

  private final Cameras cameras;

  public AnimateCamera(Cameras cameras) {
    this.cameras = cameras;
  }

  @Override
  public String name() {
    return "animate_camera";
  }

  @Override
  public String description() {
    return "Move smoothly from the current 3D viewpoint to a saved angle or an explicit "
        + "camera over durationMs. Pass 'to' as a name from list_cameras (or '"
        + Cameras.CURRENT + "' for the viewpoint get_camera reports), or omit it and pass "
        + "camera fields directly; the two forms are mutually exclusive. Explicit fields "
        + "default to the current camera, so one field animates a single-axis nudge. "
        + CameraArgs.ORBIT_SUMMARY + " The call returns only when the camera has arrived, "
        + "while the LX engine keeps rendering throughout the move. A concurrent get_frame "
        + "{'camera':'current'} shoots the interpolated position immediately and reports "
        + "midMove:true. Ease matches LX camera animation: sinusoidal (the default), "
        + "quadratic, or cubic, blended 50% with linear time. durationMs must be 1-"
        + MAX_DURATION_MS + ", leaving one second of headroom under the server's 30000ms "
        + "tool-call timeout. Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("to", Schemas.string(
        "Destination saved-camera name (case-sensitive; see list_cameras), or '"
            + Cameras.CURRENT + "'. Rejected together with explicit camera fields."));
    Map<String, Object> duration = new LinkedHashMap<>();
    duration.put("type", "integer");
    duration.put("description",
        "Move duration in milliseconds, 1-" + MAX_DURATION_MS
            + ". Longer moves are rejected before they can exceed the 30000ms call timeout.");
    properties.put("durationMs", duration);
    properties.put("ease", Schemas.enumString(
        "LX ease curve (default sinusoidal); each is blended 50% with linear time.",
        List.of("sinusoidal", "quadratic", "cubic")));
    properties.putAll(CameraArgs.schemaProperties());
    return Schemas.object(properties, List.of("durationMs"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public boolean batchable() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    int durationMs = Args.requireInt(args, "durationMs");
    if (durationMs <= 0 || durationMs > MAX_DURATION_MS) {
      return Result.error(Result.INVALID_ARGUMENT,
          "durationMs must be between 1 and " + MAX_DURATION_MS
              + " (the cap leaves 1000ms under the 30000ms tool-call timeout)");
    }

    String toName = Args.optionalString(args, "to");
    boolean explicit = CameraArgs.present(args);
    if (toName != null && explicit) {
      return Result.error(Result.INVALID_ARGUMENT,
          "to and explicit camera fields are two ways to choose the destination — pass one "
              + "or the other");
    }
    if (toName == null && !explicit) {
      return Result.error(Result.INVALID_ARGUMENT,
          "animate_camera needs 'to' or at least one explicit camera field");
    }

    Cameras.CameraAngle current = this.cameras.current(lx).angle();
    Cameras.CameraAngle destination = (toName == null)
        ? CameraArgs.merge(args, current)
        : Cameras.CURRENT.equals(toName.trim())
            ? current
            : this.cameras.lookup(toName);
    Cameras.AnimationEase ease = Cameras.AnimationEase.SINUSOIDAL;
    String easeArg = Args.optionalString(args, "ease");
    if (easeArg != null) {
      ease = Cameras.AnimationEase.parse(easeArg);
    }

    Cameras.CameraAnimation move = this.cameras.animate(lx, destination, durationMs, ease);
    String destinationName = (toName == null) ? null : toName.trim();
    return Result.okAwait(() -> {
      Cameras.CameraView arrived = move.await();
      Map<String, Object> payload = new LinkedHashMap<>();
      if (destinationName != null) {
        payload.put("to", destinationName);
      }
      payload.putAll(CameraPayload.toMap(arrived));
      payload.put("durationMs", move.durationMs());
      payload.put("ease", move.ease().wire());
      return payload;
    });
  }
}
