package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;

import chromatikmcp.domain.Clips;

public final class GetClip implements LxTool {

  @Override
  public String name() {
    return "get_clip";
  }

  @Override
  public String description() {
    return "One clip's timeline envelope: timeBase (ABSOLUTE or TEMPO — decides which "
        + "cursor fields are authoritative), referenceBpm (the fixed bpm cursor millis "
        + "are derived from — NOT the live tempo), the length/loopStart/loopEnd/"
        + "playStart/playEnd/insertMarker markers, loop flag, playhead, running, pending "
        + "(a quantized launch is scheduled but hasn't fired), hasContent, and laneCount. "
        + "Every cursor is the full object {millis, beatCount, beatBasis, formatted}; "
        + "formatted is display-only, and under TEMPO timeBase the beat fields are "
        + "authoritative. path defaults to the arrange composition "
        + "(/lx/timeline/composition) and also accepts a grid clip "
        + "(/lx/mixer/channel/N/clip/M). Marker positions are set with set_clip_marker "
        + "(NOT set_parameter); lane details come from list_clip_lanes; the composition's "
        + "extra state (arm, sync, locators) comes from get_composition.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the clip — the composition (default: /lx/timeline/composition) "
            + "or a grid clip (/lx/mixer/channel/N/clip/M)"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return true;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClip clip = Clips.resolve(lx, Args.optionalString(args, "path"));
    return Result.ok(Payloads.clip(Clips.describe(clip)));
  }
}
