package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.effect.LXEffect;

import chromatikmcp.domain.Channels;

public final class MoveEffect implements LxTool {

  @Override
  public String name() {
    return "move_effect";
  }

  @Override
  public String description() {
    return "Move an effect to a new 0-based index within its container (channel, bus, or pattern). "
        + "Returns invalid_argument if the index is out of range. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the effect to move, e.g. /lx/mixer/channel/1/effect/1"));
    properties.put("index", Schemas.integer(
        "0-based destination index within the effect list", Integer.MIN_VALUE, Integer.MAX_VALUE));
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
    LXEffect effect = Channels.moveEffect(lx, path, n.intValue());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", effect.getCanonicalPath());
    payload.put("id", effect.getId());
    payload.put("label", effect.getLabel());
    payload.put("index", effect.getIndex());
    return Result.ok(payload);
  }
}
