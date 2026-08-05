package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClip;
import heronarts.lx.command.LXCommand;

import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Cursors;
import chromatikmcp.domain.Resolve;

public final class SetClipMarker implements LxTool {

  // Wire names mirror the cursor 'at' origins and the envelope keys; insertion order is
  // the schema's advertised enum order.
  private static final Map<String, LXCommand.Clip.Marker> MARKERS = new LinkedHashMap<>();
  static {
    MARKERS.put("insertMarker", LXCommand.Clip.Marker.INSERT_MARKER);
    MARKERS.put("loopStart", LXCommand.Clip.Marker.LOOP_START);
    MARKERS.put("loopBrace", LXCommand.Clip.Marker.LOOP_BRACE);
    MARKERS.put("loopEnd", LXCommand.Clip.Marker.LOOP_END);
    MARKERS.put("loopLength", LXCommand.Clip.Marker.LOOP_LENGTH);
    MARKERS.put("playStart", LXCommand.Clip.Marker.PLAY_START);
    MARKERS.put("playEnd", LXCommand.Clip.Marker.PLAY_END);
    MARKERS.put("truncate", LXCommand.Clip.Marker.TRUNCATE);
  }

  @Override
  public String name() {
    return "set_clip_marker";
  }

  @Override
  public String description() {
    return "Set or nudge one timeline marker on a clip: insertMarker (the scrub/insert "
        + "position — this IS how you scrub the arrange timeline), loopStart, loopBrace "
        + "(moves the whole loop, preserving its length; echoes the resulting loop "
        + "start), loopEnd, loopLength, playStart, playEnd (pushing playEnd past the "
        + "current length grows the clip and gives a fresh composition its timeline), or "
        + "truncate (sets the clip length directly, rebounding the insert marker into "
        + "range). Exactly one of cursor (absolute target), moveBeats, or moveMillis "
        + "(signed relative nudge; negative moves earlier, bounded at the clip start). "
        + "Every marker setter silently clamps to its legal range — the returned cursor "
        + "is read back from the engine after the mutation and is the truth; clamped "
        + "(absolute form only) reports whether it differs from the request. The full "
        + "clip envelope is returned because markers are coupled (loop markers move "
        + "together, playEnd can grow length). path defaults to the arrange composition "
        + "(/lx/timeline/composition) and also accepts a grid clip "
        + "(/lx/mixer/channel/N/clip/M). Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the clip — the composition (default: /lx/timeline/composition) "
            + "or a grid clip (/lx/mixer/channel/N/clip/M)"));
    properties.put("marker", Schemas.enumString(
        "Which marker to move; truncate sets the clip length",
        List.copyOf(MARKERS.keySet())));
    properties.put("cursor", Schemas.cursor(
        "Absolute target position for the marker."));
    properties.put("moveBeats", Schemas.number(
        "Signed relative nudge in beats (fractions allowed) — alternative to cursor"));
    properties.put("moveMillis", Schemas.number(
        "Signed relative nudge in milliseconds — alternative to cursor"));
    return Schemas.object(properties, List.of("marker"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClip clip = Clips.resolve(lx, Args.optionalString(args, "path"));
    String markerName = Args.requireString(args, "marker");
    LXCommand.Clip.Marker marker = MARKERS.get(markerName);
    if (marker == null) {
      throw Resolve.invalidArgument(
          "Unknown marker '" + markerName + "' — expected one of: " + MARKERS.keySet());
    }

    Map<String, Object> cursorSpec = Args.optionalMap(args, "cursor");
    Double moveBeats = optionalNumber(args, "moveBeats");
    Double moveMillis = optionalNumber(args, "moveMillis");
    int forms = (cursorSpec != null ? 1 : 0) + (moveBeats != null ? 1 : 0)
        + (moveMillis != null ? 1 : 0);
    if (forms != 1) {
      throw Resolve.invalidArgument(
          "Exactly one of cursor, moveBeats, or moveMillis is required (got " + forms + ")");
    }

    Cursor requested = null;
    Cursor echoed;
    if (cursorSpec != null) {
      requested = Cursors.parse(clip, cursorSpec);
      echoed = Clips.setMarker(lx, clip, marker, requested);
    } else {
      double amount = (moveBeats != null) ? moveBeats : moveMillis;
      double magnitude = Math.abs(amount);
      Cursor increment = (moveBeats != null)
          ? clip.constructTempoCursor((int) magnitude, magnitude % 1.0)
          : clip.constructAbsoluteCursor(magnitude);
      echoed = Clips.moveMarker(lx, clip, marker, increment, amount >= 0);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("marker", markerName);
    payload.put("cursor", Payloads.cursor(clip, echoed));
    if (requested != null) {
      payload.put("clamped", !clip.CursorOp().isEqual(requested, echoed));
    }
    payload.put("clip", Payloads.clipEnvelope(Clips.envelope(clip)));
    return Result.ok(payload);
  }

  private static Double optionalNumber(Map<String, Object> args, String name) {
    Object value = args.get(name);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
      throw Resolve.invalidArgument("Optional number argument: " + name);
    }
    return number.doubleValue();
  }
}
