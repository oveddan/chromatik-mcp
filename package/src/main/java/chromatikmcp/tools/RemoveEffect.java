package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Channels;

public final class RemoveEffect implements LxTool {

  @Override
  public String name() {
    return "remove_effect";
  }

  @Override
  public String description() {
    return "Remove an effect from its container by canonical path. Returns invalid_argument "
        + "if the effect is locked. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the effect to remove, e.g. /lx/mixer/channel/1/effect/1"));
    return Schemas.object(properties, List.of("path"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    Channels.removeEffect(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    payload.put("kind", "effect");
    return Result.ok(payload);
  }
}
