package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.clip.LXClip;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

import chromatikmcp.domain.ClipLanes;
import chromatikmcp.domain.Clips;
import chromatikmcp.domain.Resolve;

public final class AddClipLane implements LxTool {

  @Override
  public String name() {
    return "add_clip_lane";
  }

  @Override
  public String description() {
    return "Add an automation lane to a clip. kind 'parameter': targetPath is a normalized "
        + "parameter (e.g. /lx/mixer/channel/1/fader) and the lane records that parameter. "
        + "kind 'pattern': targetPath is a pattern container — a channel like "
        + "/lx/mixer/channel/1 — and the lane records its pattern changes. path defaults "
        + "to the arrange composition (/lx/timeline/composition) and also accepts a grid "
        + "clip (/lx/mixer/channel/N/clip/M). Idempotent: if the lane already exists "
        + "nothing changes and the response is the existing lane with alreadyExisted:true. "
        + "Returns the lane summary read back from the engine (its path/index show where "
        + "the clip actually placed it) plus laneCount. Lane paths and indices are "
        + "POSITIONAL: they shift whenever lanes are added, removed, or moved — and "
        + "remove_modulator cascade-removes lanes recorded against the removed modulator's "
        + "parameters — so re-run list_clip_lanes rather than reuse addresses from earlier "
        + "responses. Undoable with Cmd-Z when a lane was created.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the clip — the composition (default: /lx/timeline/composition) "
            + "or a grid clip (/lx/mixer/channel/N/clip/M)"));
    properties.put("kind", Schemas.enumString(
        "Lane kind: 'parameter' (automate one parameter) or 'pattern' (record a channel's "
            + "pattern changes)",
        List.of("parameter", "pattern")));
    properties.put("targetPath", Schemas.string(
        "What the lane records: a normalized parameter path for kind 'parameter', or a "
            + "channel path for kind 'pattern'"));
    return Schemas.object(properties, List.of("kind", "targetPath"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    LXClip clip = Clips.resolve(lx, Args.optionalString(args, "path"));
    String kind = Args.requireString(args, "kind");
    String targetPath = Args.requireString(args, "targetPath");

    ClipLanes.AddedLane added = switch (kind) {
      case "parameter" -> {
        LXParameter parameter = Resolve.parameter(lx, targetPath);
        if (!(parameter instanceof LXNormalizedParameter normalized)) {
          throw Resolve.invalidArgument("kind 'parameter' requires a normalized parameter"
              + " at targetPath; found " + parameter.getClass().getSimpleName()
              + " at " + targetPath);
        }
        yield ClipLanes.addParameterLane(lx, clip, normalized);
      }
      case "pattern" -> {
        LXComponent component = Resolve.component(lx, targetPath);
        if (!(component instanceof LXPatternEngine.Container container)) {
          throw Resolve.invalidArgument("kind 'pattern' requires a pattern container"
              + " (e.g. an LXChannel) at targetPath; found "
              + component.getClass().getSimpleName() + " at " + targetPath);
        }
        yield ClipLanes.addPatternLane(lx, clip, container.getPatternEngine());
      }
      // The schema's enum already constrains kind; this guards non-validating callers
      // (apply_operations) with the same typed error instead of a MatchException.
      default -> throw Resolve.invalidArgument("kind must be 'parameter' or 'pattern': " + kind);
    };

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clipPath", Resolve.canonicalPath(clip));
    payload.put("alreadyExisted", added.alreadyExisted());
    payload.put("lane", Payloads.laneSummary(ClipLanes.summary(added.lane())));
    payload.put("laneCount", clip.lanes.size());
    return Result.ok(payload);
  }
}
