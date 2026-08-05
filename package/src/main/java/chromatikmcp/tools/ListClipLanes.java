package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Resolve;

public final class ListClipLanes implements LxTool {

  @Override
  public String name() {
    return "list_clip_lanes";
  }

  @Override
  public String description() {
    return "Every automation lane on a clip: canonical path (always <clipPath>/lane/<n>, "
        + "1-indexed — the address every lane tool takes), 0-based index, type (parameter "
        + "| pattern | midiNote | bus | globalModulation | colorPalette | audio | "
        + "textNote), label, eventCount, uiVisible, removable, and the lane's target "
        + "where it has one (parameterPath/busPath/channelPath). path defaults to the "
        + "arrange composition (/lx/timeline/composition) and also accepts a grid clip "
        + "(/lx/mixer/channel/N/clip/M). Lane paths are POSITIONAL: they shift whenever "
        + "lanes are added, removed, or moved — and removing a modulator can cascade-"
        + "remove its composition lanes — so re-list rather than reuse a path from an "
        + "earlier response. removable:false marks auto-managed lanes (bus, global "
        + "modulation, color palette, and a grid clip's MIDI/pattern lanes) that must "
        + "not be removed. Event addressing within a lane is {lanePath, index} with "
        + "index the absolute 0-based position in the lane's event list.";
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
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clipPath", Resolve.canonicalPath(clip));
    payload.put("timeBase", clip.getTimeBase().name());
    payload.put("laneCount", clip.lanes.size());
    payload.put("lanes", Payloads.laneSummaries(ClipLanes.list(clip)));
    return Result.ok(payload);
  }
}
