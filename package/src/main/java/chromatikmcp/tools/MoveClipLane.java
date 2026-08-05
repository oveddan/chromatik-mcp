package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClipLane;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Resolve;

public final class MoveClipLane implements LxTool {

  @Override
  public String name() {
    return "move_clip_lane";
  }

  @Override
  public String description() {
    return "Move an automation lane to a new 0-based index within its clip. path is the "
        + "canonical lane address (<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes. "
        + "The engine may override the request without failing: on the composition, "
        + "parameter/midiNote/pattern lanes are constrained to their channel's section and "
        + "section lanes (audio, textNote, globalModulation, colorPalette) snap across "
        + "whole sections — the response's lane.index is the ACTUAL position read back "
        + "from the engine, requestedIndex echoes the ask, and moved is false when the "
        + "lane ended up where it started. Bus lanes mirror mixer order and are rejected — "
        + "reorder the channel in the mixer instead. Lane paths and indices are "
        + "POSITIONAL: every lane crossed by the move shifts, so re-run list_clip_lanes "
        + "rather than reuse addresses from earlier responses. Undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical lane path from list_clip_lanes, e.g. /lx/timeline/composition/lane/4"));
    properties.put("index", Schemas.integer(
        "0-based destination index within the clip's lane list; the engine may constrain "
            + "it — check the returned lane.index", 0, Integer.MAX_VALUE));
    return Schemas.object(properties, List.of("path", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    int index = Args.requireInt(args, "index");
    LXClipLane<?> lane = Resolve.component(lx, path, LXClipLane.class);
    int fromIndex = lane.getIndex();

    ClipLanes.moveLane(lx, lane, index);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clipPath", Resolve.canonicalPath(lane.clip));
    payload.put("requestedIndex", index);
    payload.put("fromIndex", fromIndex);
    payload.put("moved", lane.getIndex() != fromIndex);
    payload.put("lane", Payloads.laneSummary(ClipLanes.summary(lane)));
    payload.put("laneCount", lane.clip.lanes.size());
    return Result.ok(payload);
  }
}
