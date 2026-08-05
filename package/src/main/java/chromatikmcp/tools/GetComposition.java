package chromatikmcp.tools;

import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Compositions;

public final class GetComposition implements LxTool {

  @Override
  public String name() {
    return "get_composition";
  }

  @Override
  public String description() {
    return "The arrange-timeline composition at /lx/timeline/composition: timeBase "
        + "(ABSOLUTE or TEMPO — decides which cursor fields are authoritative), "
        + "referenceBpm (the fixed bpm cursors' millis fields are derived from — NOT the "
        + "live tempo), length/loopStart/loopEnd/playStart/playEnd/insertMarker markers, "
        + "playhead, running, hasContent, armed (the timeline record-arm — a bare engine "
        + "field with no canonical path, deliberately unreachable via set_parameter), "
        + "sync, locatorCount, and a summary of every lane (see list_clip_lanes for the "
        + "per-lane fields). Every cursor is the full object {millis, beatCount, "
        + "beatBasis, formatted}; formatted is display-only, and under TEMPO timeBase the "
        + "beat fields are authoritative while millis is derived via referenceBpm. Clip "
        + "behavior parameters (timeBase, loop, referenceBpm, /lx/timeline/sync, …) are "
        + "registered parameters — read/write them with list_parameters/set_parameter on "
        + "the composition path; marker positions are NOT settable that way. Grid clips "
        + "(/lx/mixer/channel/N/clip/M) share this envelope shape via their own tools.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Schemas.noArgs();
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    return Result.ok(Payloads.composition(Compositions.describe(lx)));
  }
}
