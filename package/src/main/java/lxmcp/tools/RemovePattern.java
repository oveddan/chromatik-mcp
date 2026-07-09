package lxmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import lxmcp.domain.Channels;

public final class RemovePattern implements LxTool {

  @Override
  public String name() {
    return "remove_pattern";
  }

  @Override
  public String description() {
    return "Remove a pattern by its canonical path. Remaining sibling patterns reindex "
        + "(their 1-based paths shift), so cached paths go stale — re-list after removal. "
        + "Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the pattern, e.g. /lx/mixer/channel/1/pattern/1"));
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
    Channels.removePattern(lx, path);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("removed", path);
    return Result.ok(payload);
  }
}
