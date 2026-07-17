package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;

import chromatikmcp.domain.Channels;

public final class ActivatePattern implements LxTool {

  @Override
  public String name() {
    return "activate_pattern";
  }

  @Override
  public String description() {
    return "Activate (go to) a pattern on its channel. Only valid when the channel is in "
        + "PLAYLIST composite mode — callers on BLEND channels receive invalid_argument "
        + "(toggle the pattern's enabled parameter instead). With a transition blend "
        + "configured the switch starts as a transition (active: false until it lands). "
        + "Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the pattern to activate, e.g. /lx/mixer/channel/1/pattern/2"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("path") instanceof String path)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: path");
    }
    Channels.ActivationResult result = Channels.activatePattern(lx, path);
    LXPattern pattern = result.pattern();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", pattern.getCanonicalPath());
    payload.put("id", pattern.getId());
    payload.put("label", pattern.getLabel());
    payload.put("active", result.active());
    return Result.ok(payload);
  }
}
