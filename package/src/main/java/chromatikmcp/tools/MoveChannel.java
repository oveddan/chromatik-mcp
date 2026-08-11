package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXAbstractChannel;

import chromatikmcp.domain.Channels;

public final class MoveChannel implements LxTool {

  @Override
  public String name() {
    return "move_channel";
  }

  @Override
  public String description() {
    return "Move a channel or group to a 0-based destination index in the mixer's flat "
        + "channel list. The index is interpreted after removing the moved channel or entire "
        + "group block: moving index 0 to index 2 in [A, B, C] produces [B, C, A]. Groups "
        + "move together with all their members. This tool preserves membership: a grouped "
        + "channel must stay within its group, and a top-level channel cannot be inserted into "
        + "a group. Moving shifts 1-based paths "
        + "for the moved block, crossed siblings, and their descendants — re-list rather than "
        + "reusing cached paths. The response's oscChanges array reports every changed canonical "
        + "path (componentId, before, after). Returns invalid_argument for an out-of-range index "
        + "or a destination that would change group membership. Undoable in Chromatik with "
        + "Cmd-Z; an undo inverts every path in oscChanges with no separate signal.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("path", Schemas.string(
        "Canonical path of the channel or group to move, e.g. /lx/mixer/channel/1"));
    properties.put("index", Schemas.integer(
        "0-based destination in the mixer list after removing the moved channel/group block",
        Integer.MIN_VALUE, Integer.MAX_VALUE));
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
    Channels.ChannelMoveResult result = Channels.moveChannel(lx, path, index);
    LXAbstractChannel channel = result.channel();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("path", channel.getCanonicalPath());
    payload.put("id", channel.getId());
    payload.put("label", channel.getLabel());
    payload.put("index", channel.getIndex());
    payload.put("oscChanges", OscChanges.payload(result.oscChanges()));
    return Result.ok(payload);
  }
}
