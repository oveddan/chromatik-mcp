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

public final class RemoveAutomationPoint implements LxTool {

  @Override
  public String name() {
    return "remove_automation_point";
  }

  @Override
  public String description() {
    return "Remove one event from a clip lane by {lanePath, index}: an automation point "
        + "on a parameter lane, or any other lane type's event — except MIDI note lanes, "
        + "whose paired note-on/off events have no single-event removal. Returns the "
        + "removed event's former index and cursor plus the lane's remaining eventCount. "
        + "Event indices are positional and shift on every insert or remove — re-read "
        + "the lane rather than reuse an index from an earlier response, or pass "
        + "atCursor to fail safely if it moved. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lanePath", Schemas.string(
        "Canonical lane path (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes"));
    properties.put("index", Schemas.integer(
        "Absolute 0-based position of the event in the lane's event list",
        0, Integer.MAX_VALUE));
    properties.put("atCursor", Schemas.cursor(
        "Optional guard: expected cursor of the event at index — rejects if the lane "
            + "changed since it was read."));
    return Schemas.object(properties, List.of("lanePath", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClipLane<?> lane = Resolve.component(
        lx, Args.requireString(args, "lanePath"), LXClipLane.class);
    int index = Args.requireInt(args, "index");
    Map<String, Object> atCursorSpec = Args.optionalMap(args, "atCursor");
    Cursor atCursor = (atCursorSpec == null) ? null : Cursors.parse(lane.clip, atCursorSpec);
    Map<String, Object> removed = Payloads.event(ClipEvents.remove(lx, lane, index, atCursor));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lanePath", ClipLanes.lanePath(lane));
    payload.put("removed", removed);
    payload.put("eventCount", lane.events.size());
    return Result.ok(payload);
  }
}
