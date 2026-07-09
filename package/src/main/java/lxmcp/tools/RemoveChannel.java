package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Channels;

public final class RemoveChannel implements LxTool {

  @Override
  public String name() {
    return "remove_channel";
  }

  @Override
  public String description() {
    return "Remove a channel (or group) from the mixer by its canonical path. "
        + "Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string("Canonical path of the channel to remove, e.g. /lx/mixer/channel/1"));
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
    Channels.removeChannel(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    return Result.ok(payload);
  }
}
