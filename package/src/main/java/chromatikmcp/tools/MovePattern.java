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
    return "Move a pattern to a new 0-based index within its channel. Moving shifts the "
        + "1-based paths of the moved pattern, any sibling it crosses, and everything those "
        + "siblings own (their effects, any nested rack patterns and effects, and any "
        + "device-local modulators/modulations/triggers) — re-list rather than reusing cached "
        + "paths; the response's oscChanges array reports exactly which canonical paths changed "
        + "(componentId, before, after). It reports changes only, not components removed during "
        + "the move. "
        + "Returns invalid_argument if the index is out of range. Undoable in Chromatik with "
        + "Cmd-Z, which a human can trigger outside this session's control; an undo inverts "
        + "every path in oscChanges with no separate signal, so re-list after any move if undo "
        + "is possible.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the pattern to move, e.g. /lx/mixer/channel/1/pattern/1"));
    properties.put("index", Schemas.integer(
        "0-based destination index within the channel's pattern list", Integer.MIN_VALUE, Integer.MAX_VALUE));
    return Schemas.object(properties, List.of("path", "index"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    String path = Args.requireString(args, "path");
    int index = Args.requireInt(args, "index");
    Channels.PatternMoveResult result = Channels.movePattern(lx, path, index);
    LXPattern pattern = result.pattern();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", pattern.getCanonicalPath());
    payload.put("id", pattern.getId());
    payload.put("label", pattern.getLabel());
    payload.put("index", pattern.getIndex());
    payload.put("oscChanges", OscChanges.payload(result.oscChanges()));
    return Result.ok(payload);
  }
}
