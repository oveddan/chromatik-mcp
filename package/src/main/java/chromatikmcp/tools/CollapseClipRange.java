package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClipLane;

import chromatikmcp.domain.ClipEvents;
import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Cursors;
import chromatikmcp.domain.Resolve;

public final class CollapseClipRange implements LxTool {

  @Override
  public String name() {
    return "collapse_clip_range";
  }

  @Override
  public String description() {
    return "Collapse the automation envelope inside [from, to] on one clip lane: removes "
        + "the interior events, keeping the first and last events in the range as the "
        + "surviving boundary points (Chromatik's Collapse Envelope). Use it to flatten a "
        + "busy recorded envelope into a single segment between its endpoints. A range "
        + "holding fewer than three events has no interior — succeeds with removedCount 0 "
        + "and pushes nothing onto the undo stack; otherwise undoable in Chromatik with "
        + "Cmd-Z. Event indices across the lane shift after a collapse — re-read the "
        + "lane rather than reuse indices from an earlier response.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lanePath", Schemas.string(
        "Canonical lane path (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes"));
    properties.put("from", Schemas.cursor("Start of the range to collapse."));
    properties.put("to", Schemas.cursor("End of the range to collapse (must not be before from)."));
    return Schemas.object(properties, List.of("lanePath", "from", "to"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClipLane<?> lane = Resolve.component(
        lx, Args.requireString(args, "lanePath"), LXClipLane.class);
    Cursor from = Cursors.parse(lane.clip, Args.requireMap(args, "from"));
    Cursor to = Cursors.parse(lane.clip, Args.requireMap(args, "to"));
    int removedCount = ClipEvents.collapseRange(lx, lane, from, to);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lanePath", ClipLanes.lanePath(lane));
    payload.put("from", Payloads.cursor(lane.clip, from));
    payload.put("to", Payloads.cursor(lane.clip, to));
    payload.put("removedCount", removedCount);
    payload.put("eventCount", lane.events.size());
    return Result.ok(payload);
  }
}
