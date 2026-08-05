package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.TextNoteClipEvent;
import heronarts.lx.clip.TextNoteClipLane;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Compositions;
import chromatikmcp.domain.Cursors;
import chromatikmcp.domain.Resolve;

public final class SetClipNote implements LxTool {

  @Override
  public String name() {
    return "set_clip_note";
  }

  @Override
  public String description() {
    return "Edit the text-note event at {lanePath, index}: set its text (note), move it "
        + "(cursor — clamped between the neighboring events and the clip length), and/or "
        + "set its duration (length — floored at the minimum event length). At least one "
        + "of note/cursor/length is required. The response echoes the event state read "
        + "back from the engine, which may differ from the request due to clamping. Event "
        + "indices are positional and shift on every insert or remove — re-read the lane "
        + "rather than reuse an index from an earlier response, or pass atCursor to fail "
        + "safely if it moved. Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lanePath", Schemas.string(
        "Canonical path of the textNote lane (<clipPath>/lane/<n>, from list_clip_lanes)"));
    properties.put("index", Schemas.integer(
        "Absolute 0-based position of the event in the lane's event list",
        0, Integer.MAX_VALUE));
    properties.put("atCursor", Schemas.cursor(
        "Optional guard: expected cursor of the event at index — rejects if the lane "
            + "changed since it was read."));
    properties.put("note", Schemas.string("New note text (omit to keep)"));
    properties.put("cursor", Schemas.cursor(
        "New timeline position (omit to keep); clamped between the neighboring events "
            + "and the clip length."));
    properties.put("length", Schemas.cursor(
        "New duration (omit to keep); floored at the minimum event length."));
    return Schemas.object(properties, List.of("lanePath", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    TextNoteClipLane lane = Resolve.component(
        lx, Args.requireString(args, "lanePath"), TextNoteClipLane.class);
    int index = Args.requireInt(args, "index");
    Cursor atCursor = parseOptionalCursor(lane, args, "atCursor");
    String note = Args.optionalString(args, "note");
    Cursor moveTo = parseOptionalCursor(lane, args, "cursor");
    Cursor length = parseOptionalCursor(lane, args, "length");
    if (note == null && moveTo == null && length == null) {
      throw Resolve.invalidArgument(
          "set_clip_note requires at least one of: note, cursor, length");
    }
    TextNoteClipEvent event = Compositions.setNote(lane, index, atCursor, note, moveTo, length);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lanePath", ClipLanes.lanePath(lane));
    payload.putAll(Payloads.event(Compositions.describeNote(lane, event)));
    return Result.ok(payload);
  }

  private static Cursor parseOptionalCursor(TextNoteClipLane lane, Map<String, Object> args,
      String name) {
    Map<String, Object> spec = Args.optionalMap(args, name);
    return (spec == null) ? null : Cursors.parse(lane.clip, spec);
  }
}
