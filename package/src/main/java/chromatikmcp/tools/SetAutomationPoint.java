package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.ParameterClipEvent;
import heronarts.lx.clip.ParameterClipLane;

import chromatikmcp.domain.ClipEvents;
import chromatikmcp.domain.Cursors;
import chromatikmcp.domain.Resolve;

public final class SetAutomationPoint implements LxTool {

  @Override
  public String name() {
    return "set_automation_point";
  }

  @Override
  public String description() {
    return "Edit one existing automation point on a parameter clip lane, addressed by "
        + "{lanePath, index} (lanePath from list_clip_lanes, type 'parameter'; index is the "
        + "absolute 0-based position in the lane's event list). Applies any combination of: "
        + "cursor (move the point in time), normalized (new value in [0,1] normalized "
        + "space, not the raw parameter value — on a boolean parameter's lane it snaps to "
        + "0 or 1), curve (the interpolation type of the segment arriving AT this point "
        + "from the previous point), shape (curve shaping factor -1 to 1; 0 is the "
        + "neutral/linear shape), or resetShape (shape back to 0; mutually exclusive with "
        + "shape). At least one edit is required. cursor+normalized together apply as a "
        + "single undoable move; every other aspect is its own undo step (one Cmd-Z each, "
        + "in the order value/move, curve, shape). A move is silently clamped between the "
        + "neighboring points' cursors and the clip bounds — a point can never cross its "
        + "neighbors (remove and re-add it to jump past one). The payload echoes the point "
        + "read back from the engine (resulting index, cursor, normalized, curve, shape — "
        + "the same field names get_clip_lane emits), never the request, so any clamp or "
        + "snap is visible by comparison. Event indices are positional and shift on every "
        + "insert or remove — re-read the lane rather than reuse an index from an earlier "
        + "response, or pass atCursor to fail safely if it moved.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("lanePath", Schemas.string(
        "Canonical path of the parameter automation lane, e.g. "
            + "/lx/timeline/composition/lane/4 (see list_clip_lanes; lane paths are "
            + "positional — re-list rather than reuse one from an earlier response)"));
    properties.put("index", Schemas.integer(
        "Absolute 0-based index of the point in the lane's event list", 0, Integer.MAX_VALUE));
    properties.put("atCursor", Schemas.cursor(
        "Optional guard: expected cursor of the event at index — rejects if the lane "
            + "changed since it was read."));
    properties.put("cursor", Schemas.cursor(
        "New time position for the point (clamped between the neighboring points and the "
            + "clip bounds)."));
    properties.put("normalized", Schemas.number(
        "New value as a normalized fraction of the parameter's range, 0-1 (not the raw "
            + "parameter value). With cursor, both apply as one undoable move."));
    properties.put("curve", Schemas.enumString(
        "Interpolation curve of the segment arriving at this point from the previous point",
        List.of("POWER_EASE", "POWER_S_CURVE", "SMOOTHSTEP", "SINUSOIDAL")));
    properties.put("shape", Schemas.number(
        "Curve shaping factor, -1 to 1 (0 = neutral: linear for POWER_EASE; negative and "
            + "positive bend the segment toward its start/end)"));
    properties.put("resetShape", Schemas.bool(
        "Reset the shaping factor to 0 (mutually exclusive with shape)"));
    return Schemas.object(properties, List.of("lanePath", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String lanePath = Args.requireString(args, "lanePath");
    int index = Args.requireInt(args, "index");
    ParameterClipLane lane = Resolve.component(lx, lanePath, ParameterClipLane.class);
    Cursor atCursor = cursorArg(lane, args, "atCursor");
    Cursor toCursor = cursorArg(lane, args, "cursor");
    Double normalized = Args.optionalBoundedNumber(args, "normalized", 0, 1);
    ParameterClipEvent.Curve curve = curveArg(args);
    Double shape = Args.optionalBoundedNumber(args, "shape", -1, 1);
    boolean resetShape = Args.optionalBoolean(args, "resetShape", false);
    return Result.ok(Payloads.parameterEvent(ClipEvents.editParameterEvent(
        lx, lane, index, atCursor, toCursor, normalized, curve, shape, resetShape)));
  }

  private static Cursor cursorArg(ParameterClipLane lane, Map<String, Object> args, String name) {
    Map<String, Object> spec = Args.optionalMap(args, name);
    return (spec == null) ? null : Cursors.parse(lane.clip, spec);
  }

  private static ParameterClipEvent.Curve curveArg(Map<String, Object> args) {
    String curve = Args.optionalString(args, "curve");
    if (curve == null) {
      return null;
    }
    try {
      return ParameterClipEvent.Curve.valueOf(curve.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException x) {
      throw Resolve.invalidArgument("Unknown curve '" + curve
          + "' — expected one of: POWER_EASE, POWER_S_CURVE, SMOOTHSTEP, SINUSOIDAL");
    }
  }
}
