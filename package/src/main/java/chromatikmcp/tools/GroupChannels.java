package chromatikmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

import chromatikmcp.domain.Channels;

public final class GroupChannels implements LxTool {

  @Override
  public String name() {
    return "group_channels";
  }

  @Override
  public String description() {
    return "Create a mixer group from a non-empty list of top-level channel paths. "
        + "The leftmost selected channel determines where the group bus is inserted; members are "
        + "reordered contiguously in their current mixer order. Rejects duplicate paths, group paths, and channels "
        + "already in a group. Grouping shifts positional channel paths, including descendants; "
        + "the response reports every changed canonical path in oscChanges, and callers should "
        + "re-list channels before reusing cached paths. LX has no explicit-list grouping command, "
        + "so this is a direct engine edit. Not undoable with Cmd-Z.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    Map<String, Object> paths = new LinkedHashMap<>();
    paths.put("type", "array");
    paths.put("description", "Non-empty list of top-level channel paths to group; input order does not matter");
    paths.put("items", Schemas.string("Canonical path of a top-level channel"));
    paths.put("minItems", 1);
    properties.put("paths", paths);
    return Schemas.object(properties, List.of("paths"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }

  @Override
  public boolean batchable() {
    // apply_operations promises one undo entry per successful structural mutation.
    // Explicit-list grouping has no LXCommand and therefore cannot satisfy that contract.
    return false;
  }

  @Override
  public Result<Map<String, Object>> handle(LX lx, Map<String, Object> args) {
    List<String> paths = Args.requireStringList(args, "paths");
    Channels.GroupChannelsResult result = Channels.groupChannels(lx, paths);
    Map<String, Object> payload = ChannelGroupingPayload.channel(result.group());
    payload.put("channels", ChannelGroupingPayload.channels(result.channels()));
    payload.put("oscChanges", OscChanges.payload(result.oscChanges()));
    return Result.ok(payload);
  }
}
