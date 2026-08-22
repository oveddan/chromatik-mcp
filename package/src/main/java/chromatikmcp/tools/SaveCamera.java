package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Cameras;

public final class SaveCamera implements LxTool {

  private final Cameras cameras;

  public SaveCamera(Cameras cameras) {
    this.cameras = cameras;
  }

  @Override
  public String name() {
    return "save_camera";
  }

  @Override
  public String description() {
    return "Name the current viewpoint so it can be returned to exactly. A named angle is "
        + "what makes successive renders comparable: re-shooting a pattern from "
        + "'stage-looking-up' across tuning passes shows what the change did, while two "
        + "images shot from slightly different angles mostly show the camera move. It is "
        + "also shared vocabulary — a PR can say which angle a render came from and a "
        + "reviewer can reproduce it. Saves the live camera by default; pass camera fields "
        + "to save an angle without moving there first. " + CameraArgs.ORBIT_SUMMARY + " "
        + "Saving an existing name overwrites it (the response's 'replaced' says so). "
        + "Saved angles live in the project file, so they survive a restart — but like "
        + "every other edit here they only reach disk when save_project runs. Not undoable "
        + "with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("name", Schemas.string(
        "Name for this angle, e.g. 'stage-looking-up'. Matched exactly (case-sensitive) by "
            + "recall_camera; surrounding whitespace is trimmed."));
    properties.putAll(CameraArgs.schemaProperties());
    return Schemas.object(properties, List.of("name"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String name = Args.requireString(args, "name");
    Cameras.CameraAngle angle = CameraArgs.merge(args, this.cameras.current(lx).angle());
    Cameras.SaveResult saved = this.cameras.save(name, angle);
    Map<String, Object> payload = CameraPayload.toMap(saved.camera());
    payload.put("replaced", saved.replaced());
    return Result.ok(payload);
  }
}
