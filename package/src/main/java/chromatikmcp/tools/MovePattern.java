package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;

import chromatikmcp.domain.Channels;

public final class MovePattern implements LxTool {

  @Override
  public String name() {
    return "move_pattern";
  }

  @Override
  public String description() {
    return "Move a pattern to a new 0-based index within its channel. "
        + "Returns invalid_argument if the index is out of range. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the pattern to move, e.g. /lx/mixer/channel/1/pattern/1"));
    properties.put("index", Map.of("type", "integer", "description",
        "0-based destination index within the channel's pattern list"));
    return Schemas.object(properties, List.of("path", "index"));
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
    if (!(args.get("index") instanceof Number n)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required integer argument: index");
    }
    LXPattern pattern = Channels.movePattern(lx, path, n.intValue());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", pattern.getCanonicalPath());
    payload.put("id", pattern.getId());
    payload.put("label", pattern.getLabel());
    payload.put("index", pattern.getIndex());
    return Result.ok(payload);
  }
}
