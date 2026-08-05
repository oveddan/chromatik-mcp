package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.ParameterClipLane;

import chromatikmcp.domain.ClipEvents;
import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Cursors;
import chromatikmcp.domain.Resolve;

public final class AddAutomationPoint implements LxTool {

  @Override
  public String name() {
    return "add_automation_point";
  }

  @Override
  public String description() {
    return "Insert an automation point on a parameter clip lane (undoable). lanePath is "
        + "the lane address from list_clip_lanes (<clipPath>/lane/<n>) and must be a "
        + "parameter-type lane — create one first with add_clip_lane if needed. "
        + "normalized is the value in [0,1] normalized space; the engine clamps it "
        + "(boolean lanes snap to 0/1, trigger lanes fire on 1.0), so the payload echoes "
        + "the stored normalized, the cursor, and curve/shape read back from the created "
        + "event, plus its resulting absolute index and the lane's new eventCount. "
        + "Event indices are positional and shift on every insert or remove — re-read "
        + "the lane rather than reuse an index from an earlier response; the "
        + "event-editing tools (set_automation_point, remove_automation_point, "
        + "set_clip_note) take an atCursor guard to fail safely if it moved.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lanePath", Schemas.string(
        "Canonical path of a parameter clip lane, e.g. /lx/timeline/composition/lane/4 "
            + "(see list_clip_lanes)"));
    properties.put("cursor", Schemas.cursor("Timeline position for the new point."));
    properties.put("normalized", Schemas.number(
        "Value at this point in [0,1] normalized space (clamped by the engine; boolean "
            + "lanes snap to 0/1)"));
    return Schemas.object(properties, List.of("lanePath", "cursor", "normalized"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String lanePath = Args.requireString(args, "lanePath");
    LXClipLane<?> resolved = Resolve.component(lx, lanePath, LXClipLane.class);
    if (!(resolved instanceof ParameterClipLane lane)) {
      throw Resolve.invalidArgument(
          "add_automation_point requires a parameter lane; " + lanePath + " is a '"
              + ClipLanes.type(resolved) + "' lane (see list_clip_lanes)");
    }
    Cursor cursor = Cursors.parse(lane.clip, Args.requireMap(args, "cursor"));
    double normalized = Args.requireDouble(args, "normalized");
    // The shared envelope (lanePath/parameterPath/timeBase + detail + eventCount) is
    // built inside the primitive so this echo cannot drift from set_automation_point's.
    return Result.ok(Payloads.parameterEvent(
        ClipEvents.insertParameterEvent(lx, lane, cursor, normalized)));
  }
}
