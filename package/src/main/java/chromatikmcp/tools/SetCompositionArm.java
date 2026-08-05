package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Compositions;

public final class SetCompositionArm implements LxTool {

  @Override
  public String name() {
    return "set_composition_arm";
  }

  @Override
  public String description() {
    return "Set the arrange timeline's record-arm. The arm flag is a bare engine field "
        + "with no canonical path (/lx/timeline/arm deliberately does not resolve), so "
        + "this is its only write path — set_parameter cannot reach it. Arming while the "
        + "composition is stopped immediately launches it into recording: from the start "
        + "when the composition is empty, from the playhead when it has content (upstream "
        + "LX behavior). Disarming does NOT stop a running composition — use stop_clip. "
        + "Returns armed and running read back from the engine. Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("armed", Schemas.bool(
        "true to arm the timeline for recording (may start the composition — see tool "
            + "description), false to disarm"));
    return Schemas.object(properties, List.of("armed"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    boolean armed = Args.requireBoolean(args, "armed");
    boolean readBack = Compositions.setArm(lx, armed);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("armed", readBack);
    payload.put("running", Compositions.get(lx).isRunning());
    return Result.ok(payload);
  }
}
