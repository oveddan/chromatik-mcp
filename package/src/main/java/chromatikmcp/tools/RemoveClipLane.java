package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipLane;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Resolve;

public final class RemoveClipLane implements LxTool {

  @Override
  public String name() {
    return "remove_clip_lane";
  }

  @Override
  public String description() {
    return "Remove an automation lane from its clip. path is the canonical lane address "
        + "(<clipPath>/lane/<n>, 1-indexed) from list_clip_lanes. Only lanes reported "
        + "removable:true may be removed: auto-managed lanes (bus, globalModulation, "
        + "colorPalette everywhere; midiNote/pattern lanes on grid clips) are structural "
        + "and rejected with invalid_argument before anything changes. Returns the removed "
        + "lane's index/type/label plus the clip's resulting lane list read back from the "
        + "engine. Lane paths and indices are POSITIONAL: every surviving lane after the "
        + "removed one shifts down (and remove_modulator cascade-removes lanes on its own) "
        + "— use the returned lanes array or re-run list_clip_lanes rather than reuse "
        + "addresses from earlier responses. Undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical lane path from list_clip_lanes, e.g. /lx/timeline/composition/lane/4"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    LXClipLane<?> lane = Resolve.component(lx, path, LXClipLane.class);
    LXClip clip = lane.clip;

    // Identity of what's about to go away — unreadable after the removal commits.
    Map<String, Object> removed = new LinkedHashMap<>();
    removed.put("index", lane.getIndex());
    removed.put("type", ClipLanes.type(lane));
    removed.put("label", lane.getLabel());

    ClipLanes.removeLane(lx, lane);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clipPath", Resolve.canonicalPath(clip));
    payload.put("removed", removed);
    payload.put("laneCount", clip.lanes.size());
    payload.put("lanes", Payloads.laneSummaries(ClipLanes.list(clip)));
    return Result.ok(payload);
  }
}
