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

public final class AddClipNote implements LxTool {

  @Override
  public String name() {
    return "add_clip_note";
  }

  @Override
  public String description() {
    return "Insert a text-note event on a textNote lane (lanePath from list_clip_lanes, "
        + "form <clipPath>/lane/<n>) at a cursor position, with optional length (default "
        + "zero). Notes are annotations only — they never affect playback. Returns the "
        + "resulting event {index, cursor, note, length, end} read back from the engine. "
        + "Event indices are positional and shift on every insert or remove — re-read the "
        + "lane rather than reuse an index from an earlier response; the event-editing "
        + "tools (set_automation_point, remove_automation_point, set_clip_note) take an "
        + "atCursor guard to fail safely if it moved. Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lanePath", Schemas.string(
        "Canonical path of the textNote lane (<clipPath>/lane/<n>, from list_clip_lanes); "
            + "create one with add_notes_lane"));
    properties.put("note", Schemas.string("The note text"));
    properties.put("cursor", Schemas.cursor("Timeline position of the note."));
    properties.put("length", Schemas.cursor(
        "Optional duration of the note event (a cursor-shaped span from its position; "
            + "default zero)."));
    return Schemas.object(properties, List.of("lanePath", "note", "cursor"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    TextNoteClipLane lane = Resolve.component(
        lx, Args.requireString(args, "lanePath"), TextNoteClipLane.class);
    Cursor cursor = Cursors.parse(lane.clip, Args.requireMap(args, "cursor"));
    Map<String, Object> lengthSpec = Args.optionalMap(args, "length");
    Cursor length = (lengthSpec == null) ? null : Cursors.parse(lane.clip, lengthSpec);
    TextNoteClipEvent event = Compositions.addNote(
        lane, Args.requireString(args, "note"), cursor, length);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lanePath", ClipLanes.lanePath(lane));
    payload.putAll(Payloads.event(Compositions.describeNote(lane, event)));
    return Result.ok(payload);
  }
}
