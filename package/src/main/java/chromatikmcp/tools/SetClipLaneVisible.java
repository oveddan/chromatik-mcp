package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClipLane;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Resolve;

public final class SetClipLaneVisible implements LxTool {

  @Override
  public String name() {
    return "set_clip_lane_visible";
  }

  @Override
  public String description() {
    return "Show or hide an automation lane in the arrange/clip editor UI. Editor-only: a "
        + "hidden lane still plays back. path is the canonical lane address "
        + "(<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes; lane paths are "
        + "positional, so re-list rather than reuse one from an earlier response. Returns "
        + "the lane summary with uiVisible read back from the engine. This is a direct "
        + "engine edit (uiVisible has no command history). Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical lane path from list_clip_lanes, e.g. /lx/timeline/composition/lane/4"));
    properties.put("visible", Schemas.bool(
        "true to show the lane in the editor, false to hide it"));
    return Schemas.object(properties, List.of("path", "visible"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    boolean visible = Args.requireBoolean(args, "visible");
    LXClipLane<?> lane = Resolve.component(lx, path, LXClipLane.class);

    ClipLanes.setVisible(lane, visible);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clipPath", Resolve.canonicalPath(lane.clip));
    payload.put("lane", Payloads.laneSummary(ClipLanes.summary(lane)));
    return Result.ok(payload);
  }
}
