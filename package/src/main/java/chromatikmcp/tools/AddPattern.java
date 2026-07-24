package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.pattern.LXPattern;

import chromatikmcp.domain.Channels;

public final class AddPattern implements LxTool {

  @Override
  public String name() {
    return "add_pattern";
  }

  @Override
  public String description() {
    return "Add a pattern ('class', from list_available_patterns — either the full class "
        + "name or the short name it lists) to a channel ('containerPath'). Pass an optional 0-based "
        + "index to insert at a specific position; omit to append. The first pattern added "
        + "to an empty channel auto-activates. Targets channels only (not PatternRacks). "
        + "Inserting shifts the 1-based paths of later sibling patterns — re-list rather "
        + "than reusing cached paths. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("containerPath", Schemas.string(
        "Canonical path of the channel, e.g. /lx/mixer/channel/1"));
    properties.put("class", Schemas.string(
        "Pattern class name, as returned by list_available_patterns — full class name or "
            + "short name"));
    properties.put("index", Schemas.integer(
        "0-based insertion index; omit to append at the end", Integer.MIN_VALUE, Integer.MAX_VALUE));
    return Schemas.object(properties, List.of("containerPath", "class"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    if (!(args.get("containerPath") instanceof String channelPath)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: containerPath");
    }
    if (!(args.get("class") instanceof String typeName)) {
      return Result.error(Result.INVALID_ARGUMENT, "Required string argument: class");
    }
    int index = -1;
    if (args.get("index") instanceof Number n) {
      index = n.intValue();
    }
    Class<? extends LXPattern> patternClass = Channels.resolvePatternClass(lx, typeName);
    LXPattern pattern = Channels.addPattern(lx, channelPath, patternClass, index);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", pattern.getCanonicalPath());
    payload.put("id", pattern.getId());
    payload.put("label", pattern.getLabel());
    payload.put("class", pattern.getClass().getName());
    payload.put("index", pattern.getIndex());
    return Result.ok(payload);
  }
}
