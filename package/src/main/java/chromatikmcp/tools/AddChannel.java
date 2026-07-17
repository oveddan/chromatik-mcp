package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;

import chromatikmcp.domain.Channels;

public final class AddChannel implements LxTool {

  @Override
  public String name() {
    return "add_channel";
  }

  @Override
  public String description() {
    return "Add a new channel to the mixer. Optionally seed it with a first pattern by "
        + "passing a fully-qualified class name (from list_available_patterns). "
        + "Returns the new channel's path, id, label, and 0-based index. Note: LX also "
        + "moves UI focus/selection to the new channel. Undoable in Chromatik with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("pattern", Schemas.string(
        "Optional pattern class name (from list_available_patterns) to seed the channel with"));
    return Schemas.object(properties, List.of());
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    Object patternArg = args.get("pattern");
    if (patternArg != null && !(patternArg instanceof String)) {
      return Result.error(Result.INVALID_ARGUMENT, "pattern must be a string class name");
    }
    Class<? extends LXPattern> patternClass = null;
    if (patternArg instanceof String patternName) {
      patternClass = Channels.resolvePatternClass(lx, patternName);
    }
    LXChannel channel = Channels.addChannel(lx, patternClass);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", channel.getCanonicalPath());
    payload.put("id", channel.getId());
    payload.put("label", channel.getLabel());
    payload.put("index", channel.getIndex());
    return Result.ok(payload);
  }
}
