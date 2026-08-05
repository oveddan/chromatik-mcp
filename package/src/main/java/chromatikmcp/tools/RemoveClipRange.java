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

public final class RemoveClipRange implements LxTool {

  @Override
  public String name() {
    return "remove_clip_range";
  }

  @Override
  public String description() {
    return "Delete every event in the cursor range [from, to] (inclusive at both ends) "
        + "on one clip lane. Lane-scoped by design — LX has no clip-wide range command; "
        + "loop over the lanes from list_clip_lanes for a whole-clip cut. On MIDI note "
        + "lanes, note-on/off pairs overlapping the range are removed together. Leaves "
        + "the gap open: events after the range keep their cursors, and markers and clip "
        + "length are unchanged (compose with set_clip_marker's truncate to also shorten "
        + "the clip). A range containing no events succeeds with removedCount 0 and "
        + "pushes nothing onto the undo stack; otherwise undoable in Chromatik with "
        + "Cmd-Z. Event indices across the lane shift after a removal — re-read the "
        + "lane rather than reuse indices from an earlier response.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lanePath", Schemas.string(
        "Canonical lane path (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes"));
    properties.put("from", Schemas.cursor("Start of the range to delete."));
    properties.put("to", Schemas.cursor("End of the range to delete (must not be before from)."));
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
    int removedCount = ClipEvents.removeRange(lx, lane, from, to);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lanePath", ClipLanes.lanePath(lane));
    payload.put("from", Payloads.cursor(lane.clip, from));
    payload.put("to", Payloads.cursor(lane.clip, to));
    payload.put("removedCount", removedCount);
    payload.put("eventCount", lane.events.size());
    return Result.ok(payload);
  }
}
