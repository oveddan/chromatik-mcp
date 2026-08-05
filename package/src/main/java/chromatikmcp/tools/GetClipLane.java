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

public final class GetClipLane implements LxTool {

  @Override
  public String name() {
    return "get_clip_lane";
  }

  @Override
  public String description() {
    return "One automation lane in full: the lane summary (as in list_clip_lanes) plus a "
        + "paged read of its events. path is the lane address from list_clip_lanes "
        + "(<clipPath>/lane/<n>, 1-indexed) and works on every lane type. Each event "
        + "carries its ABSOLUTE 0-based index in the lane's event list — the address "
        + "every event mutation takes — its cursor, and type-appropriate fields: "
        + "parameter events have normalized/curve/shape, pattern events "
        + "patternLabel/patternPath, MIDI events noteOn/pitch/velocity/midiChannel, "
        + "audio events fileName/sourceLengthMs/length/end, text notes note/length/end. "
        + "from/to are an INCLUSIVE cursor window; offset (default 0) indexes into the "
        + "matched set and limit caps the page (default "
        + ClipEvents.DEFAULT_PAGE_LIMIT + ", max " + ClipEvents.MAX_PAGE_LIMIT + "). "
        + "The envelope reports eventCount (lane total), total (matched by from/to), "
        + "returned, and truncated (more matches exist past this page — advance offset). "
        + "Event indices are positional and shift on every insert or remove — re-read "
        + "the lane rather than reuse an index from an earlier response; the "
        + "event-editing tools (set_automation_point, remove_automation_point, "
        + "set_clip_note) take an atCursor guard to fail safely if it moved.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical lane path from list_clip_lanes: <clipPath>/lane/<n> (1-indexed), e.g. "
            + "/lx/timeline/composition/lane/4 or /lx/mixer/channel/1/clip/1/lane/2. "
            + "Lane paths are positional — re-list after lane mutations."));
    properties.put("from", Schemas.cursor(
        "Only events at or after this position are matched (inclusive)."));
    properties.put("to", Schemas.cursor(
        "Only events at or before this position are matched (inclusive)."));
    properties.put("offset", Schemas.integer(
        "0-based offset into the MATCHED events (after from/to filtering), default 0",
        0, Integer.MAX_VALUE));
    properties.put("limit", Schemas.integer(
        "Maximum events to return, default " + ClipEvents.DEFAULT_PAGE_LIMIT,
        1, ClipEvents.MAX_PAGE_LIMIT));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClipLane<?> lane = Resolve.component(
        lx, Args.requireString(args, "path"), LXClipLane.class);
    Map<String, Object> fromSpec = Args.optionalMap(args, "from");
    Map<String, Object> toSpec = Args.optionalMap(args, "to");
    Cursor from = (fromSpec == null) ? null : Cursors.parse(lane.clip, fromSpec);
    Cursor to = (toSpec == null) ? null : Cursors.parse(lane.clip, toSpec);
    int offset = Args.optionalInt(args, "offset", 0);
    int limit = Args.optionalInt(args, "limit", ClipEvents.DEFAULT_PAGE_LIMIT);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.putAll(Payloads.laneSummary(ClipLanes.summary(lane)));
    payload.put("clipPath", Resolve.canonicalPath(lane.clip));
    payload.put("timeBase", lane.clip.getTimeBase().name());
    // The page's eventCount matches the summary's by construction; putAll keeps them one.
    payload.putAll(Payloads.eventPage(ClipEvents.page(lane, from, to, offset, limit)));
    return Result.ok(payload);
  }
}
