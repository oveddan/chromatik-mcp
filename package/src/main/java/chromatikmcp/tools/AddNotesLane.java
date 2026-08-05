package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.TextNoteClipLane;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Compositions;
import chromatikmcp.domain.Resolve;

public final class AddNotesLane implements LxTool {

  @Override
  public String name() {
    return "add_notes_lane";
  }

  @Override
  public String description() {
    return "Add a text-notes lane to the arrange composition (/lx/timeline/composition): "
        + "a lane of timestamped annotation events (section names, cues, TODOs) that "
        + "never affects playback. The lane is appended at the end of the lane list; pass "
        + "an optional label to name it — multiple notes lanes are allowed and otherwise "
        + "indistinguishable. Returns {clipPath, lane, laneCount} — the same envelope as "
        + "the other lane-creating tools; add events with "
        + "add_clip_note. Lane paths are positional: they shift whenever lanes are added, "
        + "removed, or moved, so re-run list_clip_lanes rather than reuse a path from an "
        + "earlier response. Undoable in Chromatik with Cmd-Z (undo removes the lane; the "
        + "optional label rename is not a separate undo step).";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("label", Schemas.string(
        "Optional display label for the lane (default \"Notes\")"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    TextNoteClipLane lane = Compositions.addTextNoteLane(
        lx, Args.optionalString(args, "label"));
    // Shared lane-creation envelope: {clipPath, lane, laneCount}, as in add_clip_lane
    // and add_audio_lane, so agents can read result.lane.path across the family.
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clipPath", Resolve.canonicalPath(lane.clip));
    payload.put("lane", Payloads.laneSummary(ClipLanes.summary(lane)));
    payload.put("laneCount", lane.clip.lanes.size());
    return Result.ok(payload);
  }
}
